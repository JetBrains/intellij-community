// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.hprof

import com.intellij.diagnostic.hprof.parser.ConstantPoolEntry
import com.intellij.diagnostic.hprof.parser.HProfEventBasedParser
import com.intellij.diagnostic.hprof.parser.HProfVisitor
import com.intellij.diagnostic.hprof.parser.HeapDumpRecordType
import com.intellij.diagnostic.hprof.parser.InstanceFieldEntry
import com.intellij.diagnostic.hprof.parser.RecordType
import com.intellij.diagnostic.hprof.parser.StaticFieldEntry
import com.intellij.diagnostic.hprof.parser.Type
import com.intellij.diagnostic.hprof.util.HProfReadBufferSlidingWindow
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.ArrayDeque

@ApiStatus.Internal
object PersistentSyntaxTreeHprofProcessor {
  const val VERSIONED_PAYLOAD_MAP2_CLASS_NAME: String = "com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap2"
  const val ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME: String = "com.intellij.psi.impl.source.tree.mvcc.ArrayVersionedPayloadMap"

  fun extractVersionedPayloadMaps(hprofPath: Path, indicator: ProgressIndicator? = null): VersionedPayloadMapExtraction {
    FileChannel.open(hprofPath, StandardOpenOption.READ).use { channel ->
      HProfEventBasedParser(channel).use { parser ->
        val collector = TargetObjectCollector(indicator)
        parser.accept(collector, "Collect persistent syntax tree payload maps")

        val arrayReader = ReferencedArrayReader(collector.referencedVersionArrayIds, collector.referencedPayloadArrayIds, indicator)
        parser.accept(arrayReader, "Read persistent syntax tree payload arrays")

        return collector.buildExtraction(arrayReader.versionArrays, arrayReader.payloadArrays)
      }
    }
  }

  fun analyzePersistentSyntaxTreeOverhead(
    hprofPath: Path,
    indicator: ProgressIndicator? = null,
    objectSizeLayout: HprofObjectSizeLayout = HprofObjectSizeLayout.DEFAULT,
  ): PersistentSyntaxTreeOverheadAnalysis {
    FileChannel.open(hprofPath, StandardOpenOption.READ).use { channel ->
      HProfEventBasedParser(channel).use { parser ->
        val collector = TargetObjectCollector(indicator, objectSizeLayout)
        parser.accept(collector, "Collect persistent syntax tree heap graph")

        val arrayReader = ReferencedArrayReader(collector.referencedVersionArrayIds, collector.referencedPayloadArrayIds, indicator)
        parser.accept(arrayReader, "Read persistent syntax tree payload arrays")

        val extraction = collector.buildExtraction(arrayReader.versionArrays, arrayReader.payloadArrays)
        val graphNodes = collector.buildGraphNodes(arrayReader.versionArrays, arrayReader.payloadArrays)
        return PersistentSyntaxTreeOverheadAnalyzer(collector.heapGraph ?: error("Heap graph is not collected"), graphNodes, extraction, indicator).analyze()
      }
    }
  }

  private class TargetObjectCollector(
    private val indicator: ProgressIndicator?,
    private val objectSizeLayout: HprofObjectSizeLayout? = null,
  ) : HProfVisitor() {
    private val strings = HashMap<Long, String>()
    private val classNameStringIds = HashMap<Long, Long>()
    private val classes = HashMap<Long, ClassInfo>()
    private val objectTypeNames = HashMap<Long, String>()

    val heapGraph: HeapGraph? = objectSizeLayout?.let { HeapGraph() }

    private val map2Instances = ArrayList<Map2Instance>()
    private val arrayMapInstances = ArrayList<ArrayMapInstance>()

    val referencedVersionArrayIds = HashSet<Long>()
    val referencedPayloadArrayIds = HashSet<Long>()

    override fun preVisit() {
      disableAll()
      enable(RecordType.StringInUTF8)
      enable(RecordType.LoadClass)
      enable(HeapDumpRecordType.ClassDump)
      enable(HeapDumpRecordType.InstanceDump)
      enable(HeapDumpRecordType.PrimitiveArrayDump)
      enable(HeapDumpRecordType.ObjectArrayDump)
    }

    override fun visitStringInUTF8(id: Long, s: String) {
      strings[id] = s
    }

    override fun visitLoadClass(classSerialNumber: Long, classObjectId: Long, stackSerialNumber: Long, classNameStringId: Long) {
      classNameStringIds[classObjectId] = classNameStringId
    }

    override fun visitClassDump(
      classId: Long,
      stackTraceSerialNumber: Long,
      superClassId: Long,
      classloaderClassId: Long,
      instanceSize: Long,
      constants: Array<ConstantPoolEntry>,
      staticFields: Array<StaticFieldEntry>,
      instanceFields: Array<InstanceFieldEntry>,
    ) {
      val name = strings[classNameStringIds[classId]]?.let(::normalizeClassName)
      classes[classId] = ClassInfo(name, superClassId, instanceSize, instanceFields.toList())
    }

    override fun visitPrimitiveArrayDump(
      arrayObjectId: Long,
      stackTraceSerialNumber: Long,
      numberOfElements: Long,
      elementType: Type,
      primitiveArrayData: HProfReadBufferSlidingWindow,
    ) {
      val className = elementType.getClassNameOfPrimitiveArray()
      objectTypeNames[arrayObjectId] = className
      heapGraph?.objects?.put(
        arrayObjectId,
        HeapObjectInfo(
          className = className,
          shallowSize = requireNotNull(objectSizeLayout).primitiveArraySize(numberOfElements, elementType),
          references = LongArray(0),
        ),
      )
      indicator?.checkCanceled()
    }

    override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
      val className = className(arrayClassObjectId)
      if (className != null) {
        objectTypeNames[arrayObjectId] = className
      }
      heapGraph?.objects?.put(
        arrayObjectId,
        HeapObjectInfo(
          className = className,
          shallowSize = requireNotNull(objectSizeLayout).objectArraySize(objects.size, sizeOfID),
          references = objects.nonNullReferences(),
        ),
      )
      indicator?.checkCanceled()
    }

    override fun visitInstanceDump(objectId: Long, stackTraceSerialNumber: Long, classObjectId: Long, bytes: HProfReadBufferSlidingWindow) {
      val className = className(classObjectId)
      if (className != null) {
        objectTypeNames[objectId] = className
      }

      heapGraph?.objects?.put(
        objectId,
        HeapObjectInfo(
          className = className,
          shallowSize = requireNotNull(objectSizeLayout).instanceSize(classes[classObjectId]?.instanceSize ?: 0L),
          references = readObjectReferences(classObjectId, bytes),
        ),
      )

      when (className) {
        VERSIONED_PAYLOAD_MAP2_CLASS_NAME -> map2Instances += readMap2(objectId, classObjectId, bytes)
        ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME -> readArrayMap(objectId, classObjectId, bytes).also {
          arrayMapInstances += it
          referencedVersionArrayIds += it.versionsArrayId
          referencedPayloadArrayIds += it.payloadsArrayId
        }
      }
      indicator?.checkCanceled()
    }

    fun buildExtraction(versionArrays: Map<Long, LongArray>, payloadArrays: Map<Long, LongArray>): VersionedPayloadMapExtraction {
      return VersionedPayloadMapExtraction(
        map2Instances = map2Instances.map(::toExtractedMap2),
        arrayMapInstances = arrayMapInstances.map { toExtractedArrayMap(it, versionArrays, payloadArrays) },
      )
    }

    fun buildGraphNodes(versionArrays: Map<Long, LongArray>, payloadArrays: Map<Long, LongArray>): Map<Long, VersionedPayloadMapGraphNode> {
      val result = HashMap<Long, VersionedPayloadMapGraphNode>()
      for (instance in map2Instances) {
        val extracted = toExtractedMap2(instance)
        result[instance.objectId] = VersionedPayloadMapGraphNode(
          objectId = instance.objectId,
          entries = extracted.entries,
          structuralObjectIds = LongArray(0),
        )
      }
      for (instance in arrayMapInstances) {
        val extracted = toExtractedArrayMap(instance, versionArrays, payloadArrays)
        result[instance.objectId] = VersionedPayloadMapGraphNode(
          objectId = instance.objectId,
          entries = extracted.entries,
          structuralObjectIds = longArrayOf(instance.versionsArrayId, instance.payloadsArrayId).nonNullReferences(),
        )
      }
      return result
    }

    private fun readMap2(objectId: Long, classObjectId: Long, bytes: HProfReadBufferSlidingWindow): Map2Instance {
      val fields = readFields(classObjectId, bytes)
      return Map2Instance(
        objectId,
        fields.longField("version1"),
        fields.objectField("payload1"),
        fields.longField("version2"),
        fields.objectField("payload2"),
      )
    }

    private fun readArrayMap(objectId: Long, classObjectId: Long, bytes: HProfReadBufferSlidingWindow): ArrayMapInstance {
      val fields = readFields(classObjectId, bytes)
      return ArrayMapInstance(
        objectId,
        fields.objectField("versions"),
        fields.objectField("payloads"),
      )
    }

    private fun readFields(classObjectId: Long, bytes: HProfReadBufferSlidingWindow): Map<String, FieldValue> {
      val result = HashMap<String, FieldValue>()
      var offset = 0
      for (field in collectInstanceFields(classObjectId)) {
        require(offset + fieldSize(field.type) <= bytes.limit()) {
          "Instance data is too short for field ${fieldName(field)} of ${className(classObjectId)}, offset=$offset, size=${bytes.limit()}"
        }
        val fieldName = fieldName(field)
        if (fieldName != null) {
          result[fieldName] = FieldValue(field.type, readFieldValue(bytes, offset, field.type))
        }
        offset += fieldSize(field.type)
      }
      return result
    }

    private fun readObjectReferences(classObjectId: Long, bytes: HProfReadBufferSlidingWindow): LongArray {
      val result = ArrayList<Long>()
      var offset = 0
      for (field in collectInstanceFields(classObjectId)) {
        require(offset + fieldSize(field.type) <= bytes.limit()) {
          "Instance data is too short for object reference field ${fieldName(field)} of ${className(classObjectId)}, offset=$offset, size=${bytes.limit()}"
        }
        if (field.type == Type.OBJECT) {
          val objectId = readObjectId(bytes, offset)
          if (objectId != 0L) {
            result += objectId
          }
        }
        offset += fieldSize(field.type)
      }
      return result.toLongArray()
    }

    private fun collectInstanceFields(classObjectId: Long): List<InstanceFieldEntry> {
      val result = ArrayList<InstanceFieldEntry>()
      var current: ClassInfo? = classes[classObjectId]
      while (current != null) {
        result += current.instanceFields
        current = classes[current.superClassId]
      }
      return result
    }

    private fun readFieldValue(bytes: HProfReadBufferSlidingWindow, offset: Int, type: Type): Long {
      return when (type) {
        Type.OBJECT -> readObjectId(bytes, offset)
        Type.BOOLEAN,
        Type.BYTE,
          -> bytes.get(offset).toLong()
        Type.CHAR -> bytes.getChar(offset).code.toLong()
        Type.SHORT -> bytes.getShort(offset).toLong()
        Type.INT -> bytes.getInt(offset).toLong()
        Type.FLOAT -> bytes.getFloat(offset).toRawBits().toLong()
        Type.LONG -> bytes.getLong(offset)
        Type.DOUBLE -> bytes.getDouble(offset).toRawBits()
      }
    }

    private fun readObjectId(bytes: HProfReadBufferSlidingWindow, offset: Int): Long {
      return when (sizeOfID) {
        1 -> bytes.get(offset).toLong()
        2 -> bytes.getShort(offset).toLong()
        4 -> bytes.getInt(offset).toLong()
        8 -> bytes.getLong(offset)
        else -> error("ID size is not assigned: $sizeOfID")
      }
    }

    private fun fieldSize(type: Type): Int = if (type == Type.OBJECT) sizeOfID else type.size

    private fun fieldName(field: InstanceFieldEntry): String? = strings[field.fieldNameStringId]

    private fun className(classObjectId: Long): String? = classes[classObjectId]?.name

    private fun toExtractedMap2(instance: Map2Instance): VersionedPayloadMapInstance {
      return VersionedPayloadMapInstance(
        objectId = instance.objectId,
        layout = VersionedPayloadMapLayout.MAP2,
        className = VERSIONED_PAYLOAD_MAP2_CLASS_NAME,
        entries = listOf(
          entry(instance.version1, instance.payload1ObjectId),
          entry(instance.version2, instance.payload2ObjectId),
        ),
      )
    }

    private fun toExtractedArrayMap(
      instance: ArrayMapInstance,
      versionArrays: Map<Long, LongArray>,
      payloadArrays: Map<Long, LongArray>,
    ): VersionedPayloadMapInstance {
      val versions = versionArrays[instance.versionsArrayId]
                     ?: error("versions array is not found: mapObjectId=${instance.objectId}, arrayObjectId=${instance.versionsArrayId}")
      val payloads = payloadArrays[instance.payloadsArrayId]
                     ?: error("payloads array is not found: mapObjectId=${instance.objectId}, arrayObjectId=${instance.payloadsArrayId}")
      require(versions.size == payloads.size) {
        "VersionedPayloadMap arrays have different sizes: versions=${versions.size}, payloads=${payloads.size}, objectId=${instance.objectId}"
      }

      return VersionedPayloadMapInstance(
        objectId = instance.objectId,
        layout = VersionedPayloadMapLayout.ARRAY,
        className = ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME,
        entries = versions.indices.map { index -> entry(versions[index], payloads[index]) },
      )
    }

    private fun entry(version: Long, payloadObjectId: Long): VersionedPayloadEntry {
      return VersionedPayloadEntry(
        version = version,
        payloadObjectId = payloadObjectId.takeIf { it != 0L },
        payloadClassName = payloadObjectId.takeIf { it != 0L }?.let(objectTypeNames::get),
      )
    }
  }

  private class ReferencedArrayReader(
    private val versionArrayIds: Set<Long>,
    private val payloadArrayIds: Set<Long>,
    private val indicator: ProgressIndicator?,
  ) : HProfVisitor() {
    val versionArrays = HashMap<Long, LongArray>()
    val payloadArrays = HashMap<Long, LongArray>()

    override fun preVisit() {
      disableAll()
      enable(HeapDumpRecordType.PrimitiveArrayDump)
      enable(HeapDumpRecordType.ObjectArrayDump)
    }

    override fun visitPrimitiveArrayDump(
      arrayObjectId: Long,
      stackTraceSerialNumber: Long,
      numberOfElements: Long,
      elementType: Type,
      primitiveArrayData: HProfReadBufferSlidingWindow,
    ) {
      if (arrayObjectId !in versionArrayIds) return
      require(elementType == Type.LONG) { "Expected long[] for objectId=$arrayObjectId, got $elementType" }
      require(numberOfElements <= Int.MAX_VALUE) { "Array is too large: objectId=$arrayObjectId, size=$numberOfElements" }
      versionArrays[arrayObjectId] = LongArray(numberOfElements.toInt()) { primitiveArrayData.getLong() }
      indicator?.checkCanceled()
    }

    override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
      if (arrayObjectId !in payloadArrayIds) return
      payloadArrays[arrayObjectId] = objects
      indicator?.checkCanceled()
    }
  }

  private data class ClassInfo(
    val name: String?,
    val superClassId: Long,
    val instanceSize: Long,
    val instanceFields: List<InstanceFieldEntry>,
  )

  private data class FieldValue(val type: Type, val value: Long)

  private data class Map2Instance(
    val objectId: Long,
    val version1: Long,
    val payload1ObjectId: Long,
    val version2: Long,
    val payload2ObjectId: Long,
  )

  private data class ArrayMapInstance(
    val objectId: Long,
    val versionsArrayId: Long,
    val payloadsArrayId: Long,
  )

  private class VersionedPayloadMapGraphNode(
    val objectId: Long,
    val entries: List<VersionedPayloadEntry>,
    val structuralObjectIds: LongArray,
  ) {
    private val latestVersion: Long? = entries.maxOfOrNull { it.version }

    fun latestPayloadIds(): List<Long> {
      val version = latestVersion ?: return emptyList()
      return entries.mapNotNull { entry -> entry.payloadObjectId?.takeIf { entry.version == version } }
    }

    fun stalePayloadIds(): List<Long> {
      val version = latestVersion ?: return emptyList()
      return entries.mapNotNull { entry -> entry.payloadObjectId?.takeIf { entry.version < version } }
    }
  }

  private class PersistentSyntaxTreeOverheadAnalyzer(
    private val graph: HeapGraph,
    private val graphNodes: Map<Long, VersionedPayloadMapGraphNode>,
    private val extraction: VersionedPayloadMapExtraction,
    private val indicator: ProgressIndicator?,
  ) {
    private val graphNodeObjectIds: Set<Long> = graphNodes.keys

    fun analyze(): PersistentSyntaxTreeOverheadAnalysis {
      val latestEntryRoots = HashSet<Long>()
      val staleEntryRoots = HashSet<Long>()
      for (node in graphNodes.values) {
        latestEntryRoots += node.latestPayloadIds()
        staleEntryRoots += node.stalePayloadIds()
      }

      val staleOnlyMapObjectIds = collectReachableMapObjectIds(staleEntryRoots)
      staleOnlyMapObjectIds.removeAll(collectReachableMapObjectIds(latestEntryRoots))

      val liveMapObjectIds = HashSet(graphNodeObjectIds)
      liveMapObjectIds.removeAll(staleOnlyMapObjectIds)

      val staleRoots = HashSet<Long>()
      for (node in graphNodes.values) {
        if (node.objectId in staleOnlyMapObjectIds) {
          staleRoots += node.objectId
        }
        else {
          staleRoots += node.stalePayloadIds()
        }
      }

      val liveStructuralObjectIds = collectStructuralObjectIds(liveMapObjectIds)
      val liveReachableObjectIds = collectReachable(liveMapObjectIds, liveMapObjectIds, liveStructuralObjectIds)
      val staleReachableObjectIds = collectReachable(staleRoots, emptySet(), emptySet())

      val retainedObjectIds = HashSet(staleReachableObjectIds)
      retainedObjectIds.removeAll(liveReachableObjectIds)
      retainedObjectIds.removeIf { it !in graph.objects }

      val retainedObjectsByClass = buildRetainedObjectsByClass(retainedObjectIds)
      return PersistentSyntaxTreeOverheadAnalysis(
        extraction = extraction,
        retainedOverheadBytes = retainedObjectsByClass.sumOf { it.retainedBytes },
        retainedObjectCount = retainedObjectIds.size,
        retainedObjectIds = retainedObjectIds,
        staleRootCount = staleRoots.size,
        liveRootCount = liveMapObjectIds.size,
        staleReachableObjectCount = staleReachableObjectIds.count { it in graph.objects },
        liveReachableObjectCount = liveReachableObjectIds.count { it in graph.objects },
        staleOnlyMapObjectIds = staleOnlyMapObjectIds,
        retainedObjectsByClass = retainedObjectsByClass,
      )
    }

    private fun collectReachableMapObjectIds(roots: Collection<Long>): HashSet<Long> {
      val reachableObjectIds = collectReachable(roots, emptySet(), emptySet())
      val result = HashSet<Long>()
      for (objectId in reachableObjectIds) {
        if (objectId in graphNodeObjectIds) {
          result += objectId
        }
      }
      return result
    }

    private fun collectReachable(
      roots: Collection<Long>,
      liveMapObjectIds: Set<Long>,
      liveStructuralObjectIds: Set<Long>,
    ): HashSet<Long> {
      val result = HashSet<Long>()
      val stack = ArrayDeque<Long>()
      for (root in roots) {
        if (root != 0L) {
          stack.add(root)
        }
      }

      while (!stack.isEmpty()) {
        val objectId = stack.removeLast()
        if (objectId == 0L || !result.add(objectId)) {
          continue
        }
        indicator?.checkCanceled()

        val liveMapNode = graphNodes[objectId]
        if (liveMapNode != null && objectId in liveMapObjectIds) {
          liveMapNode.structuralObjectIds.addTo(stack)
          liveMapNode.latestPayloadIds().addTo(stack)
          continue
        }
        if (objectId in liveStructuralObjectIds) {
          continue
        }

        graph.objects[objectId]?.references?.addTo(stack)
      }
      return result
    }

    private fun collectStructuralObjectIds(mapObjectIds: Set<Long>): HashSet<Long> {
      val result = HashSet<Long>()
      for (mapObjectId in mapObjectIds) {
        graphNodes[mapObjectId]?.structuralObjectIds?.forEach { objectId ->
          if (objectId != 0L) {
            result += objectId
          }
        }
      }
      return result
    }

    private fun buildRetainedObjectsByClass(objectIds: Set<Long>): List<PersistentSyntaxTreeOverheadClassStats> {
      val mutableStats = HashMap<String, MutableClassStats>()
      for (objectId in objectIds) {
        val heapObject = graph.objects[objectId] ?: continue
        mutableStats.computeIfAbsent(heapObject.className ?: UNKNOWN_CLASS_NAME) { MutableClassStats() }.add(heapObject.shallowSize)
      }
      return mutableStats.map { (className, stats) ->
        PersistentSyntaxTreeOverheadClassStats(className, stats.retainedObjectCount, stats.retainedBytes)
      }.sortedWith(compareByDescending<PersistentSyntaxTreeOverheadClassStats> { it.retainedBytes }.thenBy { it.className })
    }
  }

  private class MutableClassStats {
    var retainedObjectCount: Int = 0
      private set
    var retainedBytes: Long = 0
      private set

    fun add(bytes: Long) {
      retainedObjectCount++
      retainedBytes += bytes
    }
  }

  private class HeapGraph {
    val objects = HashMap<Long, HeapObjectInfo>()
  }

  private class HeapObjectInfo(
    val className: String?,
    val shallowSize: Long,
    val references: LongArray,
  )

  private fun Map<String, FieldValue>.longField(name: String): Long {
    val value = this[name] ?: error("Field '$name' is not found")
    require(value.type == Type.LONG) { "Field '$name' is not a long: ${value.type}" }
    return value.value
  }

  private fun Map<String, FieldValue>.objectField(name: String): Long {
    val value = this[name] ?: error("Field '$name' is not found")
    require(value.type == Type.OBJECT) { "Field '$name' is not an object reference: ${value.type}" }
    return value.value
  }

  private fun LongArray.nonNullReferences(): LongArray {
    var size = 0
    for (value in this) {
      if (value != 0L) {
        size++
      }
    }
    if (size == this.size) {
      return this
    }

    val result = LongArray(size)
    var index = 0
    for (value in this) {
      if (value != 0L) {
        result[index++] = value
      }
    }
    return result
  }

  private fun LongArray.addTo(stack: ArrayDeque<Long>) {
    for (value in this) {
      if (value != 0L) {
        stack.add(value)
      }
    }
  }

  private fun Collection<Long>.addTo(stack: ArrayDeque<Long>) {
    for (value in this) {
      if (value != 0L) {
        stack.add(value)
      }
    }
  }

  private fun normalizeClassName(name: String): String = name.replace('/', '.')

  private const val UNKNOWN_CLASS_NAME: String = "<unknown>"
}

@ApiStatus.Internal
data class VersionedPayloadMapExtraction(
  val map2Instances: List<VersionedPayloadMapInstance>,
  val arrayMapInstances: List<VersionedPayloadMapInstance>,
) {
  val allInstances: List<VersionedPayloadMapInstance>
    get() = map2Instances + arrayMapInstances
}

@ApiStatus.Internal
data class VersionedPayloadMapInstance(
  val objectId: Long,
  val layout: VersionedPayloadMapLayout,
  val className: String,
  val entries: List<VersionedPayloadEntry>,
)

@ApiStatus.Internal
enum class VersionedPayloadMapLayout {
  MAP2,
  ARRAY,
}

@ApiStatus.Internal
data class VersionedPayloadEntry(
  val version: Long,
  val payloadObjectId: Long?,
  val payloadClassName: String?,
)

@ApiStatus.Internal
data class HprofObjectSizeLayout(
  val objectPreambleSize: Int = 8,
  val arrayPreambleSize: Int = 12,
) {
  fun instanceSize(instanceDataSize: Long): Long = objectPreambleSize + instanceDataSize

  fun objectArraySize(numberOfElements: Int, idSize: Int): Long = arrayPreambleSize + numberOfElements.toLong() * idSize

  fun primitiveArraySize(numberOfElements: Long, elementType: Type): Long = arrayPreambleSize + numberOfElements * elementType.size

  companion object {
    val DEFAULT: HprofObjectSizeLayout = HprofObjectSizeLayout()
  }
}

@ApiStatus.Internal
data class PersistentSyntaxTreeOverheadAnalysis(
  val extraction: VersionedPayloadMapExtraction,
  val retainedOverheadBytes: Long,
  val retainedObjectCount: Int,
  val retainedObjectIds: Set<Long>,
  val staleRootCount: Int,
  val liveRootCount: Int,
  val staleReachableObjectCount: Int,
  val liveReachableObjectCount: Int,
  val staleOnlyMapObjectIds: Set<Long>,
  val retainedObjectsByClass: List<PersistentSyntaxTreeOverheadClassStats>,
)

@ApiStatus.Internal
data class PersistentSyntaxTreeOverheadClassStats(
  val className: String,
  val retainedObjectCount: Int,
  val retainedBytes: Long,
)
