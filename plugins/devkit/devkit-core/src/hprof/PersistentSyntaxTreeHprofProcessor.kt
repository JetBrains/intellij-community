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
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.platform.util.progress.reportSequentialProgress
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@ApiStatus.Internal
object PersistentSyntaxTreeHprofProcessor {
  const val VERSIONED_PAYLOAD_MAP1_CLASS_NAME: String = "com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap1"
  const val VERSIONED_PAYLOAD_MAP2_CLASS_NAME: String = "com.intellij.psi.impl.source.tree.mvcc.VersionedPayloadMap2"
  const val ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME: String = "com.intellij.psi.impl.source.tree.mvcc.ArrayVersionedPayloadMap"

  fun extractVersionedPayloadMaps(hprofPath: Path, indicator: ProgressIndicator? = null): VersionedPayloadMapExtraction {
    val collector = collectTargetObjects(hprofPath, indicator, objectSizeLayout = null)
    val arrayReader = readReferencedArrays(hprofPath, collector.referencedVersionArrayIds, collector.referencedPayloadArrayIds, indicator)
    return collector.buildExtraction(arrayReader.versionArrays, arrayReader.payloadArrays)
  }

  suspend fun analyzePersistentSyntaxTreeOverheadWithProgress(
    project: Project,
    hprofPath: Path,
    objectSizeLayout: HprofObjectSizeLayout = HprofObjectSizeLayout.DEFAULT,
  ): PersistentSyntaxTreeOverheadAnalysis {
    return withBackgroundProgress(project, DevKitBundle.message("persistent.syntax.tree.hprof.action.progress.title")) {
      reportSequentialProgress { reporter ->
        val collector = reporter.nextStep(50, DevKitBundle.message("persistent.syntax.tree.hprof.progress.collect.graph")) {
          runHprofProcessingOnIo { indicator ->
            collectTargetObjects(hprofPath, indicator, objectSizeLayout)
          }
        }
        val arrayReader = reporter.nextStep(75, DevKitBundle.message("persistent.syntax.tree.hprof.progress.read.arrays")) {
          runHprofProcessingOnIo { indicator ->
            readReferencedArrays(hprofPath, collector.referencedVersionArrayIds, collector.referencedPayloadArrayIds, indicator)
          }
        }
        val (extraction, graphNodes) = reporter.nextStep(85, DevKitBundle.message("persistent.syntax.tree.hprof.progress.build.result")) {
          collector.buildExtractionAndGraphNodesInParallel(arrayReader.versionArrays, arrayReader.payloadArrays)
        }
        reporter.nextStep(100, DevKitBundle.message("persistent.syntax.tree.hprof.progress.analyze.retained")) {
          withContext(Dispatchers.Default) {
            reportProgressScope { progressReporter ->
              PersistentSyntaxTreeOverheadAnalyzer(
                graph = collector.heapGraph ?: error("Heap graph is not collected"),
                graphNodes = graphNodes,
                extraction = extraction,
                indicator = null,
                progressReporter = progressReporter,
              ).analyze()
            }
          }
        }
      }
    }
  }

  suspend fun analyzePersistentSyntaxTreeOverhead(
    hprofPath: Path,
    indicator: ProgressIndicator? = null,
    objectSizeLayout: HprofObjectSizeLayout = HprofObjectSizeLayout.DEFAULT,
  ): PersistentSyntaxTreeOverheadAnalysis {
    val collector = collectTargetObjects(hprofPath, indicator, objectSizeLayout)
    val arrayReader = readReferencedArrays(hprofPath, collector.referencedVersionArrayIds, collector.referencedPayloadArrayIds, indicator)
    val (extraction, graphNodes) = collector.buildExtractionAndGraphNodes(arrayReader.versionArrays, arrayReader.payloadArrays)
    return PersistentSyntaxTreeOverheadAnalyzer(
      graph = collector.heapGraph ?: error("Heap graph is not collected"),
      graphNodes = graphNodes,
      extraction = extraction,
      indicator = indicator,
      progressReporter = null,
    ).analyze()
  }

  private suspend fun <T> runHprofProcessingOnIo(action: (ProgressIndicator) -> T): T {
    return withContext(Dispatchers.IO) {
      coroutineToIndicator(action)
    }
  }

  private fun collectTargetObjects(
    hprofPath: Path,
    indicator: ProgressIndicator?,
    objectSizeLayout: HprofObjectSizeLayout?,
  ): TargetObjectCollector {
    FileChannel.open(hprofPath, StandardOpenOption.READ).use { channel ->
      HProfEventBasedParser(channel).use { parser ->
        val collector = TargetObjectCollector(indicator, channel.size(), objectSizeLayout)
        val progressKey = if (objectSizeLayout == null) {
          "persistent.syntax.tree.hprof.progress.collect.maps"
        }
        else {
          "persistent.syntax.tree.hprof.progress.collect.graph"
        }
        val progressText = DevKitBundle.message(progressKey)
        prepareIndicator(indicator, progressText)
        parser.accept(collector, progressText)
        return collector
      }
    }
  }

  private fun readReferencedArrays(
    hprofPath: Path,
    versionArrayIds: LongSet,
    payloadArrayIds: LongSet,
    indicator: ProgressIndicator?,
  ): ReferencedArrayReader {
    FileChannel.open(hprofPath, StandardOpenOption.READ).use { channel ->
      HProfEventBasedParser(channel).use { parser ->
        val arrayReader = ReferencedArrayReader(versionArrayIds, payloadArrayIds, indicator, channel.size())
        prepareIndicator(indicator, DevKitBundle.message("persistent.syntax.tree.hprof.progress.read.arrays"))
        parser.accept(arrayReader, DevKitBundle.message("persistent.syntax.tree.hprof.progress.read.arrays"))
        return arrayReader
      }
    }
  }

  private fun prepareIndicator(indicator: ProgressIndicator?, text: @ProgressText String) {
    indicator ?: return
    indicator.isIndeterminate = false
    indicator.text = text
    indicator.text2 = null
    indicator.fraction = 0.0
  }

  private abstract class ProgressReportingHProfVisitor(
    private val indicator: ProgressIndicator?,
    private val fileSize: Long,
  ) : HProfVisitor() {
    private var nextProgressUpdateOffset: Long = 0

    protected fun updateProgress() {
      val indicator = indicator ?: return
      indicator.checkCanceled()
      if (fileSize <= 0) {
        return
      }

      val offset = heapRecordOffset
      if (offset >= nextProgressUpdateOffset) {
        indicator.fraction = (offset.toDouble() / fileSize).coerceIn(0.0, 1.0)
        nextProgressUpdateOffset = offset + PROGRESS_UPDATE_BYTES
      }
    }

    override fun postVisit() {
      indicator?.fraction = 1.0
    }
  }

  private class TargetObjectCollector(
    indicator: ProgressIndicator?,
    fileSize: Long,
    private val objectSizeLayout: HprofObjectSizeLayout? = null,
  ) : ProgressReportingHProfVisitor(indicator, fileSize) {
    private val strings = Long2ObjectOpenHashMap<String>()
    private val classNameStringIds = Long2LongOpenHashMap()
    private val classes = Long2ObjectOpenHashMap<ClassInfo>()
    private val objectTypeNames = if (objectSizeLayout == null) Long2ObjectOpenHashMap<String>() else null
    private val instanceFieldsByClass = Long2ObjectOpenHashMap<List<InstanceFieldEntry>>()
    private val objectReferenceOffsetsByClass = Long2ObjectOpenHashMap<IntArray>()
    private val shallowSizesByClass = Long2LongOpenHashMap()

    val heapGraph: HeapGraph? = objectSizeLayout?.let { HeapGraph() }

    private val map1Instances = ArrayList<Map1Instance>()
    private val map2Instances = ArrayList<Map2Instance>()
    private val arrayMapInstances = ArrayList<ArrayMapInstance>()

    val referencedVersionArrayIds = LongOpenHashSet()
    val referencedPayloadArrayIds = LongOpenHashSet()

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
      strings.put(id, s)
      updateProgress()
    }

    override fun visitLoadClass(classSerialNumber: Long, classObjectId: Long, stackSerialNumber: Long, classNameStringId: Long) {
      classNameStringIds.put(classObjectId, classNameStringId)
      updateProgress()
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
      val name = strings.get(classNameStringIds.get(classId))?.let(::normalizeClassName)
      classes.put(classId, ClassInfo(name, superClassId, instanceFields.toList()))
      heapGraph?.putClass(classId, name)
      updateProgress()
    }

    override fun visitPrimitiveArrayDump(
      arrayObjectId: Long,
      stackTraceSerialNumber: Long,
      numberOfElements: Long,
      elementType: Type,
      primitiveArrayData: HProfReadBufferSlidingWindow,
    ) {
      val className = elementType.getClassNameOfPrimitiveArray()
      storeObjectTypeName(arrayObjectId, className)
      heapGraph?.putPrimitiveArray(
        arrayObjectId,
        className,
        requireNotNull(objectSizeLayout).primitiveArraySize(numberOfElements, elementType),
      )
      updateProgress()
    }

    override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
      val className = className(arrayClassObjectId)
      if (className != null) {
        storeObjectTypeName(arrayObjectId, className)
      }
      heapGraph?.put(
        arrayObjectId,
        arrayClassObjectId,
        requireNotNull(objectSizeLayout).objectArraySize(objects.size),
        objects.nonNullReferences(),
      )
      updateProgress()
    }

    override fun visitInstanceDump(objectId: Long, stackTraceSerialNumber: Long, classObjectId: Long, bytes: HProfReadBufferSlidingWindow) {
      val className = className(classObjectId)
      if (className != null) {
        storeObjectTypeName(objectId, className)
      }

      heapGraph?.put(
        objectId,
        classObjectId,
        shallowSize(classObjectId),
        readObjectReferences(classObjectId, bytes),
      )

      when (className) {
        VERSIONED_PAYLOAD_MAP1_CLASS_NAME -> map1Instances += readMap1(objectId, classObjectId, bytes)
        VERSIONED_PAYLOAD_MAP2_CLASS_NAME -> map2Instances += readMap2(objectId, classObjectId, bytes)
        ARRAY_VERSIONED_PAYLOAD_MAP_CLASS_NAME -> readArrayMap(objectId, classObjectId, bytes).also {
          arrayMapInstances += it
          referencedVersionArrayIds.add(it.versionsArrayId)
          referencedPayloadArrayIds.add(it.payloadsArrayId)
        }
      }
      updateProgress()
    }

    fun buildExtraction(versionArrays: Long2ObjectMap<LongArray>, payloadArrays: Long2ObjectMap<LongArray>): VersionedPayloadMapExtraction {
      return VersionedPayloadMapExtraction(
        map1Instances = map1Instances.map(::toExtractedMap1),
        map2Instances = map2Instances.map(::toExtractedMap2),
        arrayMapInstances = arrayMapInstances.map { toExtractedArrayMap(it, versionArrays, payloadArrays) },
      )
    }

    suspend fun buildExtractionAndGraphNodesInParallel(
      versionArrays: Long2ObjectMap<LongArray>,
      payloadArrays: Long2ObjectMap<LongArray>,
    ): Pair<VersionedPayloadMapExtraction, Map<Long, VersionedPayloadMapGraphNode>> = coroutineScope {
      val map1 = async(Dispatchers.Default) { buildMap1ExtractionAndGraphNodes() }
      val map2 = async(Dispatchers.Default) { buildMap2ExtractionAndGraphNodes() }
      val arrayMap = async(Dispatchers.Default) { buildArrayMapExtractionAndGraphNodes(versionArrays, payloadArrays) }
      combineExtractionAndGraphNodes(map1.await(), map2.await(), arrayMap.await())
    }

    fun buildExtractionAndGraphNodes(
      versionArrays: Long2ObjectMap<LongArray>,
      payloadArrays: Long2ObjectMap<LongArray>,
    ): Pair<VersionedPayloadMapExtraction, Map<Long, VersionedPayloadMapGraphNode>> {
      return combineExtractionAndGraphNodes(
        buildMap1ExtractionAndGraphNodes(),
        buildMap2ExtractionAndGraphNodes(),
        buildArrayMapExtractionAndGraphNodes(versionArrays, payloadArrays),
      )
    }

    private fun buildMap1ExtractionAndGraphNodes(): ExtractedGraphNodes {
      val instances = ArrayList<VersionedPayloadMapInstance>(map1Instances.size)
      val nodes = HashMap<Long, VersionedPayloadMapGraphNode>(map1Instances.size)
      for (instance in map1Instances) {
        val extracted = toExtractedMap1(instance)
        instances += extracted
        nodes[instance.objectId] = VersionedPayloadMapGraphNode(
          objectId = instance.objectId,
          entries = extracted.entries,
          structuralObjectIds = EMPTY_LONG_ARRAY,
        )
      }
      return ExtractedGraphNodes(instances, nodes)
    }

    private fun buildMap2ExtractionAndGraphNodes(): ExtractedGraphNodes {
      val instances = ArrayList<VersionedPayloadMapInstance>(map2Instances.size)
      val nodes = HashMap<Long, VersionedPayloadMapGraphNode>(map2Instances.size)
      for (instance in map2Instances) {
        val extracted = toExtractedMap2(instance)
        instances += extracted
        nodes[instance.objectId] = VersionedPayloadMapGraphNode(
          objectId = instance.objectId,
          entries = extracted.entries,
          structuralObjectIds = EMPTY_LONG_ARRAY,
        )
      }
      return ExtractedGraphNodes(instances, nodes)
    }

    private fun buildArrayMapExtractionAndGraphNodes(
      versionArrays: Long2ObjectMap<LongArray>,
      payloadArrays: Long2ObjectMap<LongArray>,
    ): ExtractedGraphNodes {
      val instances = ArrayList<VersionedPayloadMapInstance>(arrayMapInstances.size)
      val nodes = HashMap<Long, VersionedPayloadMapGraphNode>(arrayMapInstances.size)
      for (instance in arrayMapInstances) {
        val extracted = toExtractedArrayMap(instance, versionArrays, payloadArrays)
        instances += extracted
        nodes[instance.objectId] = VersionedPayloadMapGraphNode(
          objectId = instance.objectId,
          entries = extracted.entries,
          structuralObjectIds = longArrayOf(instance.versionsArrayId, instance.payloadsArrayId).nonNullReferences(),
        )
      }
      return ExtractedGraphNodes(instances, nodes)
    }

    private fun combineExtractionAndGraphNodes(
      map1: ExtractedGraphNodes,
      map2: ExtractedGraphNodes,
      arrayMap: ExtractedGraphNodes,
    ): Pair<VersionedPayloadMapExtraction, Map<Long, VersionedPayloadMapGraphNode>> {
      val graphNodes = HashMap<Long, VersionedPayloadMapGraphNode>(map1.graphNodes.size + map2.graphNodes.size + arrayMap.graphNodes.size)
      graphNodes.putAll(map1.graphNodes)
      graphNodes.putAll(map2.graphNodes)
      graphNodes.putAll(arrayMap.graphNodes)
      return VersionedPayloadMapExtraction(
        map1Instances = map1.instances,
        map2Instances = map2.instances,
        arrayMapInstances = arrayMap.instances,
      ) to graphNodes
    }

    private fun readMap1(objectId: Long, classObjectId: Long, bytes: HProfReadBufferSlidingWindow): Map1Instance {
      val fields = readFields(classObjectId, bytes)
      return Map1Instance(
        objectId,
        fields.longField("version"),
        fields.objectField("payload"),
      )
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
      val offsets = objectReferenceOffsets(classObjectId)
      if (offsets.isEmpty()) {
        return EMPTY_LONG_ARRAY
      }

      val result = LongArray(offsets.size)
      var resultSize = 0
      for (offset in offsets) {
        require(offset + sizeOfID <= bytes.limit()) {
          "Instance data is too short for object reference field of ${className(classObjectId)}, offset=$offset, size=${bytes.limit()}"
        }
        val objectId = readObjectId(bytes, offset)
        if (objectId != 0L) {
          result[resultSize++] = objectId
        }
      }
      return when (resultSize) {
        0 -> EMPTY_LONG_ARRAY
        result.size -> result
        else -> result.copyOf(resultSize)
      }
    }

    private fun collectInstanceFields(classObjectId: Long): List<InstanceFieldEntry> {
      instanceFieldsByClass.get(classObjectId)?.let { return it }
      val result = ArrayList<InstanceFieldEntry>()
      var current: ClassInfo? = classes.get(classObjectId)
      while (current != null) {
        result += current.instanceFields
        current = classes.get(current.superClassId)
      }
      instanceFieldsByClass.put(classObjectId, result)
      return result
    }

    private fun objectReferenceOffsets(classObjectId: Long): IntArray {
      objectReferenceOffsetsByClass.get(classObjectId)?.let { return it }
      val offsets = ArrayList<Int>()
      var offset = 0
      for ((_, type) in collectInstanceFields(classObjectId)) {
        if (type == Type.OBJECT) {
          offsets += offset
        }
        offset += fieldSize(type)
      }
      return offsets.toIntArray().also { objectReferenceOffsetsByClass.put(classObjectId, it) }
    }

    private fun shallowSize(classObjectId: Long): Long {
      val layout = objectSizeLayout ?: return 0L
      if (shallowSizesByClass.containsKey(classObjectId)) {
        return shallowSizesByClass.get(classObjectId)
      }

      var instanceDataSize = 0L
      for ((_, type) in collectInstanceFields(classObjectId)) {
        instanceDataSize += layout.fieldSize(type)
      }
      return layout.instanceSize(instanceDataSize).also { shallowSizesByClass.put(classObjectId, it) }
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

    private fun className(classObjectId: Long): String? = classes.get(classObjectId)?.name

    private fun storeObjectTypeName(objectId: Long, className: String) {
      objectTypeNames?.put(objectId, className)
    }

    private fun toExtractedMap1(instance: Map1Instance): VersionedPayloadMapInstance {
      return VersionedPayloadMapInstance(
        objectId = instance.objectId,
        layout = VersionedPayloadMapLayout.MAP1,
        className = VERSIONED_PAYLOAD_MAP1_CLASS_NAME,
        entries = listOf(entry(instance.version, instance.payloadObjectId)),
      )
    }

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
      versionArrays: Long2ObjectMap<LongArray>,
      payloadArrays: Long2ObjectMap<LongArray>,
    ): VersionedPayloadMapInstance {
      val versions = versionArrays.get(instance.versionsArrayId)
                     ?: error("versions array is not found: mapObjectId=${instance.objectId}, arrayObjectId=${instance.versionsArrayId}")
      val payloads = payloadArrays.get(instance.payloadsArrayId)
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
        payloadClassName = payloadObjectId.takeIf { it != 0L }?.let(::objectTypeName),
      )
    }

    private fun objectTypeName(objectId: Long): String? {
      return heapGraph?.className(objectId) ?: objectTypeNames?.get(objectId)
    }
  }

  private class ReferencedArrayReader(
    private val versionArrayIds: LongSet,
    private val payloadArrayIds: LongSet,
    indicator: ProgressIndicator?,
    fileSize: Long,
  ) : ProgressReportingHProfVisitor(indicator, fileSize) {
    val versionArrays = Long2ObjectOpenHashMap<LongArray>()
    val payloadArrays = Long2ObjectOpenHashMap<LongArray>()

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
      updateProgress()
      if (!versionArrayIds.contains(arrayObjectId)) return
      require(elementType == Type.LONG) { "Expected long[] for objectId=$arrayObjectId, got $elementType" }
      require(numberOfElements <= Int.MAX_VALUE) { "Array is too large: objectId=$arrayObjectId, size=$numberOfElements" }
      versionArrays.put(arrayObjectId, LongArray(numberOfElements.toInt()) { primitiveArrayData.getLong() })
    }

    override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
      updateProgress()
      if (!payloadArrayIds.contains(arrayObjectId)) return
      payloadArrays.put(arrayObjectId, objects)
    }
  }

  private data class ClassInfo(
    val name: String?,
    val superClassId: Long,
    val instanceFields: List<InstanceFieldEntry>,
  )

  private data class FieldValue(val type: Type, val value: Long)

  private data class Map1Instance(
    val objectId: Long,
    val version: Long,
    val payloadObjectId: Long,
  )

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

  private class ExtractedGraphNodes(
    val instances: List<VersionedPayloadMapInstance>,
    val graphNodes: Map<Long, VersionedPayloadMapGraphNode>,
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
    private val progressReporter: ProgressReporter?,
  ) {
    private val graphNodeObjectIds: LongSet = LongOpenHashSet(graphNodes.keys)
    private val progressLock = Any()

    suspend fun analyze(): PersistentSyntaxTreeOverheadAnalysis = coroutineScope {
      updateProgressText(DevKitBundle.message("persistent.syntax.tree.hprof.progress.collect.roots"))
      val latestEntryRoots = LongOpenHashSet()
      val staleEntryRoots = LongOpenHashSet()
      for (node in graphNodes.values) {
        node.latestPayloadIds().addTo(latestEntryRoots)
        node.stalePayloadIds().addTo(staleEntryRoots)
      }

      val staleReachableMapObjectIds = async(Dispatchers.Default) {
        collectReachableMapObjectIds(
          staleEntryRoots,
          DevKitBundle.message("persistent.syntax.tree.hprof.progress.traverse.stale.payloads"),
        )
      }
      val latestReachableMapObjectIds = async(Dispatchers.Default) {
        collectReachableMapObjectIds(
          latestEntryRoots,
          DevKitBundle.message("persistent.syntax.tree.hprof.progress.traverse.latest.payloads"),
        )
      }

      updateProgressText(DevKitBundle.message("persistent.syntax.tree.hprof.progress.classify.maps"))
      val staleOnlyMapObjectIds = staleReachableMapObjectIds.await()
      staleOnlyMapObjectIds.removeAll(latestReachableMapObjectIds.await())

      val liveMapObjectIds = LongOpenHashSet(graphNodeObjectIds)
      liveMapObjectIds.removeAll(staleOnlyMapObjectIds)

      val staleRoots = LongOpenHashSet()
      for (node in graphNodes.values) {
        if (staleOnlyMapObjectIds.contains(node.objectId)) {
          staleRoots.add(node.objectId)
        }
        else {
          node.stalePayloadIds().addTo(staleRoots)
        }
      }

      val liveStructuralObjectIds = collectStructuralObjectIds(liveMapObjectIds)
      val liveReachableObjectIdsDeferred = async(Dispatchers.Default) {
        collectReachable(
          liveMapObjectIds,
          liveMapObjectIds,
          liveStructuralObjectIds,
          DevKitBundle.message("persistent.syntax.tree.hprof.progress.traverse.live"),
        )
      }
      val staleReachableObjectIdsDeferred = async(Dispatchers.Default) {
        collectReachable(
          staleRoots,
          LongSets.EMPTY_SET,
          LongSets.EMPTY_SET,
          DevKitBundle.message("persistent.syntax.tree.hprof.progress.traverse.stale"),
        )
      }
      val liveReachableObjectIds = liveReachableObjectIdsDeferred.await()
      val staleReachableObjectIds = staleReachableObjectIdsDeferred.await()

      val retainedObjectIds = LongOpenHashSet(staleReachableObjectIds)
      retainedObjectIds.removeAll(liveReachableObjectIds)
      retainedObjectIds.removeUnknownObjects()

      val retainedObjectsByClassDeferred = async(Dispatchers.Default) {
        withProgressText(DevKitBundle.message("persistent.syntax.tree.hprof.progress.build.retained.stats")) {
          buildRetainedObjectsByClass(retainedObjectIds)
        }
      }
      val staleReachableObjectCountDeferred = async(Dispatchers.Default) { staleReachableObjectIds.countKnownObjects() }
      val liveReachableObjectCountDeferred = async(Dispatchers.Default) { liveReachableObjectIds.countKnownObjects() }
      val retainedObjectsByClass = retainedObjectsByClassDeferred.await()
      return@coroutineScope PersistentSyntaxTreeOverheadAnalysis(
        extraction = extraction,
        retainedOverheadBytes = retainedObjectsByClass.sumOf { it.retainedBytes },
        retainedObjectCount = retainedObjectIds.size,
        retainedObjectIds = retainedObjectIds,
        staleRootCount = staleRoots.size,
        liveRootCount = liveMapObjectIds.size,
        staleReachableObjectCount = staleReachableObjectCountDeferred.await(),
        liveReachableObjectCount = liveReachableObjectCountDeferred.await(),
        staleOnlyMapObjectIds = staleOnlyMapObjectIds,
        retainedObjectsByClass = retainedObjectsByClass,
      )
    }

    private suspend fun collectReachableMapObjectIds(roots: LongSet, progressText: @ProgressText String): LongOpenHashSet {
      val reachableObjectIds = collectReachable(roots, LongSets.EMPTY_SET, LongSets.EMPTY_SET, progressText)
      val result = LongOpenHashSet()
      val iterator = reachableObjectIds.iterator()
      while (iterator.hasNext()) {
        val objectId = iterator.nextLong()
        if (graphNodeObjectIds.contains(objectId)) {
          result.add(objectId)
        }
      }
      return result
    }

    private suspend fun collectReachable(
      roots: LongSet,
      liveMapObjectIds: LongSet,
      liveStructuralObjectIds: LongSet,
      progressText: @ProgressText String,
    ): LongOpenHashSet {
      return withProgressText(progressText) {
        val result = LongOpenHashSet()
        val stack = LongArrayList()
        var nextProgressUpdate = REACHABILITY_PROGRESS_UPDATE_OBJECTS
        val rootsIterator = roots.iterator()
        while (rootsIterator.hasNext()) {
          val root = rootsIterator.nextLong()
          if (root != 0L) {
            stack.add(root)
          }
        }

        while (!stack.isEmpty) {
          val objectId = stack.removeLong(stack.size - 1)
          if (objectId == 0L || !result.add(objectId)) {
            continue
          }
          indicator?.checkCanceled()
          if (result.size >= nextProgressUpdate) {
            updateProgressText(DevKitBundle.message("persistent.syntax.tree.hprof.progress.visited.objects", progressText, result.size))
            nextProgressUpdate = result.size + REACHABILITY_PROGRESS_UPDATE_OBJECTS
          }

          val liveMapNode = graphNodes[objectId]
          if (liveMapNode != null && liveMapObjectIds.contains(objectId)) {
            liveMapNode.structuralObjectIds.addTo(stack)
            liveMapNode.latestPayloadIds().addTo(stack)
            continue
          }
          if (liveStructuralObjectIds.contains(objectId)) {
            continue
          }

          graph.references(objectId).addTo(stack)
        }
        result
      }
    }

    private suspend fun <T> withProgressText(text: @ProgressText String, action: suspend () -> T): T {
      updateProgressText(text)
      val reporter = progressReporter ?: return action()
      return reporter.indeterminateStep(text) {
        action()
      }
    }

    private suspend fun updateProgressText(text: @ProgressText String) {
      currentCoroutineContext().ensureActive()
      val indicator = indicator ?: return
      indicator.checkCanceled()
      synchronized(progressLock) {
        indicator.text2 = text
      }
    }

    private fun collectStructuralObjectIds(mapObjectIds: LongSet): LongOpenHashSet {
      val result = LongOpenHashSet()
      val iterator = mapObjectIds.iterator()
      while (iterator.hasNext()) {
        val mapObjectId = iterator.nextLong()
        graphNodes[mapObjectId]?.structuralObjectIds?.forEach { objectId ->
          if (objectId != 0L) {
            result.add(objectId)
          }
        }
      }
      return result
    }

    private fun buildRetainedObjectsByClass(objectIds: LongSet): List<PersistentSyntaxTreeOverheadClassStats> {
      val mutableStats = HashMap<String, MutableClassStats>()
      val iterator = objectIds.iterator()
      while (iterator.hasNext()) {
        val objectId = iterator.nextLong()
        mutableStats.computeIfAbsent(graph.className(objectId) ?: UNKNOWN_CLASS_NAME) { MutableClassStats() }.add(graph.shallowSize(objectId))
      }
      return mutableStats.map { (className, stats) ->
        PersistentSyntaxTreeOverheadClassStats(className, stats.retainedObjectCount, stats.retainedBytes)
      }.sortedWith(compareByDescending<PersistentSyntaxTreeOverheadClassStats> { it.retainedBytes }.thenBy { it.className })
    }

    private fun LongOpenHashSet.removeUnknownObjects() {
      val iterator = iterator()
      while (iterator.hasNext()) {
        if (!graph.containsObject(iterator.nextLong())) {
          iterator.remove()
        }
      }
    }

    private fun LongSet.countKnownObjects(): Int {
      var result = 0
      val iterator = iterator()
      while (iterator.hasNext()) {
        val objectId = iterator.nextLong()
        if (graph.containsObject(objectId)) {
          result++
        }
      }
      return result
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
    private val classNamesByClassId = Long2ObjectOpenHashMap<String>()
    private val classIds = Long2LongOpenHashMap()
    private val primitiveArrayClassNames = Long2ObjectOpenHashMap<String>()
    private val shallowSizes = Long2LongOpenHashMap()
    private val references = Long2ObjectOpenHashMap<LongArray>()

    fun putClass(classId: Long, className: String?) {
      if (className != null) {
        classNamesByClassId.put(classId, className)
      }
    }

    fun put(objectId: Long, classId: Long, shallowSize: Long, references: LongArray) {
      if (classId != 0L) {
        classIds.put(objectId, classId)
      }
      shallowSizes.put(objectId, shallowSize)
      if (references.isNotEmpty()) {
        this.references.put(objectId, references)
      }
    }

    fun putPrimitiveArray(objectId: Long, className: String, shallowSize: Long) {
      primitiveArrayClassNames.put(objectId, className)
      shallowSizes.put(objectId, shallowSize)
    }

    fun containsObject(objectId: Long): Boolean = shallowSizes.containsKey(objectId)

    fun className(objectId: Long): String? = primitiveArrayClassNames.get(objectId) ?: classNamesByClassId.get(classIds.get(objectId))

    fun shallowSize(objectId: Long): Long = shallowSizes.get(objectId)

    fun references(objectId: Long): LongArray = references.get(objectId) ?: EMPTY_LONG_ARRAY
  }

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

  private fun LongArray.addTo(stack: LongArrayList) {
    for (value in this) {
      if (value != 0L) {
        stack.add(value)
      }
    }
  }

  private fun Collection<Long>.addTo(stack: LongArrayList) {
    for (value in this) {
      if (value != 0L) {
        stack.add(value)
      }
    }
  }

  private fun Collection<Long>.addTo(set: LongSet) {
    for (value in this) {
      if (value != 0L) {
        set.add(value)
      }
    }
  }

  private fun normalizeClassName(name: String): String = name.replace('/', '.')

  private val EMPTY_LONG_ARRAY: LongArray = LongArray(0)

  private const val UNKNOWN_CLASS_NAME: String = "<unknown>"
  private const val PROGRESS_UPDATE_BYTES: Long = 16L * 1024L * 1024L
  private const val REACHABILITY_PROGRESS_UPDATE_OBJECTS: Int = 100_000
}

@ApiStatus.Internal
data class VersionedPayloadMapExtraction(
  val map1Instances: List<VersionedPayloadMapInstance>,
  val map2Instances: List<VersionedPayloadMapInstance>,
  val arrayMapInstances: List<VersionedPayloadMapInstance>,
) {
  val allInstances: List<VersionedPayloadMapInstance>
    get() = map1Instances + map2Instances + arrayMapInstances
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
  MAP1,
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
  val referenceSize: Int = 8,
  val objectAlignment: Int = 8,
) {
  fun instanceSize(instanceDataSize: Long): Long = align(objectPreambleSize + instanceDataSize)

  fun objectArraySize(numberOfElements: Int): Long = align(arrayPreambleSize + numberOfElements.toLong() * referenceSize)

  fun primitiveArraySize(numberOfElements: Long, elementType: Type): Long = align(arrayPreambleSize + numberOfElements * elementType.size)

  fun fieldSize(type: Type): Int = if (type == Type.OBJECT) referenceSize else type.size

  private fun align(size: Long): Long {
    if (objectAlignment <= 1) {
      return size
    }
    return ((size + objectAlignment - 1) / objectAlignment) * objectAlignment
  }

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
