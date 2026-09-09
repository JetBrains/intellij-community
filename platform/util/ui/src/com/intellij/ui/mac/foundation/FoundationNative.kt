package com.intellij.ui.mac.foundation

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.GroupLayout
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_DOUBLE
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
internal object FoundationNative {
  private object Bindings {
    init {
      check(SystemInfoRt.isMac) { "Foundation requires macOS" }
    }

    val linker: Linker = Linker.nativeLinker()
    val lookup: SymbolLookup =
      SymbolLookup.libraryLookup("/System/Library/Frameworks/Foundation.framework/Foundation", Arena.global())
        .or(SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global()))
        .or(SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global()))
    val coreGraphics: SymbolLookup by lazy {
      SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Arena.global())
    }
    val functions = ConcurrentHashMap<Pair<String, FunctionDescriptor>, MethodHandle>()
    val signatures = ConcurrentHashMap<Long, ObjcSignature>()
  }

  @JvmStatic
  fun init() {
    Bindings.linker
  }

  @JvmStatic
  fun call(name: String, descriptor: FunctionDescriptor, vararg arguments: Any?): Any? = Arena.ofConfined().use { arena ->
    require(arguments.size == descriptor.argumentLayouts().size) { "Invalid argument count for $name" }
    val nativeArguments = descriptor.argumentLayouts().mapIndexed { index, layout ->
      nativeValue(ObjcType(code(layout), layout), arguments[index], arena)
    }
    function(name, descriptor).invokeWithArguments(nativeArguments)
  }

  private fun function(name: String, descriptor: FunctionDescriptor): MethodHandle =
    Bindings.functions.computeIfAbsent(name to descriptor) {
      val lookup = if (name.startsWith("CG")) Bindings.coreGraphics else Bindings.lookup
      Bindings.linker.downcallHandle(lookup.find(name).orElseThrow { UnsatisfiedLinkError("Cannot find native function: $name") },
                                     descriptor)
    }

  private fun code(layout: java.lang.foreign.MemoryLayout): Char = when (layout) {
    ADDRESS -> '^'
    JAVA_BYTE -> 'c'
    JAVA_SHORT -> 's'
    JAVA_INT -> 'i'
    JAVA_LONG -> 'q'
    JAVA_FLOAT -> 'f'
    JAVA_DOUBLE -> 'd'
    is GroupLayout -> '{'
    else -> error("Unsupported native layout: $layout")
  }

  @JvmStatic
  fun invoke(receiver: ID?, selector: Selector, arguments: Array<out Any?>): Any? {
    if (receiver == null || receiver == ID.NIL) return null
    val signature = signature(receiver, selector)
    require(signature.arguments.size == arguments.size + 2) {
      "Invalid argument count for ${selector.name}: expected ${signature.arguments.size - 2}, got ${arguments.size}"
    }
    require(signature.result.layout !is GroupLayout) { "A structure result requires an explicit native binding: ${selector.name}" }
    return Arena.ofConfined().use { arena ->
      val nativeArguments = ArrayList<Any?>(arguments.size + 2)
      val buffers = mutableListOf<Pair<ByteArray, MemorySegment>>()
      nativeArguments.add(receiver.asMemorySegment())
      nativeArguments.add(selector.asMemorySegment())
      arguments.forEachIndexed { index, argument ->
        val converted = nativeValue(signature.arguments[index + 2], argument, arena)
        nativeArguments.add(converted)
        if (argument is ByteArray) buffers.add(argument to converted as MemorySegment)
      }
      val result = try {
        function("objc_msgSend", signature.descriptor).invokeWithArguments(nativeArguments)
      }
      finally {
        for ((bytes, segment) in buffers) MemorySegment.copy(segment, 0, MemorySegment.ofArray(bytes), 0, bytes.size.toLong())
      }
      when (signature.result.code) {
        'C' -> (result as Byte).toInt() and 0xff
        'S' -> (result as Short).toInt() and 0xffff
        'I', 'L' -> Integer.toUnsignedLong(result as Int)
        else -> result
      }
    }
  }

  private fun signature(receiver: ID, selector: Selector): ObjcSignature {
    val objectClass = call("object_getClass", FunctionDescriptor.of(ADDRESS, ADDRESS), receiver) as MemorySegment
    val method = call("class_getInstanceMethod", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS), objectClass, selector) as MemorySegment
    if (method.address() != 0L) {
      return Bindings.signatures.computeIfAbsent(method.address()) {
        val encoding = call("method_getTypeEncoding", FunctionDescriptor.of(ADDRESS, ADDRESS), method) as MemorySegment
        ObjcTypeEncoding(string(encoding)).signature()
      }
    }
    val methodSignature = rawSend(receiver.asMemorySegment(),
                                  "methodSignatureForSelector:",
                                  ADDRESS,
                                  arrayOf(ADDRESS),
                                  selector.asMemorySegment()) as MemorySegment
    check(methodSignature.address() != 0L) { "Cannot find the Objective-C signature for ${selector.name}" }
    val result = rawSend(methodSignature, "methodReturnType", ADDRESS, emptyArray()) as MemorySegment
    val count = rawSend(methodSignature, "numberOfArguments", JAVA_LONG, emptyArray()) as Long
    val encoding = buildString {
      append(string(result))
      repeat(Math.toIntExact(count)) { index ->
        append(string(rawSend(methodSignature, "getArgumentTypeAtIndex:", ADDRESS, arrayOf(JAVA_LONG), index.toLong()) as MemorySegment))
      }
    }
    return ObjcTypeEncoding(encoding).signature()
  }

  private fun rawSend(receiver: MemorySegment, name: String, result: ValueLayout, arguments: Array<ValueLayout>, vararg values: Any): Any? {
    val selector = call("sel_registerName", FunctionDescriptor.of(ADDRESS, ADDRESS), name) as MemorySegment
    val descriptor = FunctionDescriptor.of(result, ADDRESS, ADDRESS, *arguments)
    return function("objc_msgSend", descriptor).invokeWithArguments(listOf(receiver, selector) + values)
  }

  @JvmStatic
  fun string(pointer: MemorySegment): String = pointer.reinterpret(Long.MAX_VALUE).getString(0)

  private fun nativeValue(type: ObjcType, value: Any?, arena: Arena): Any? {
    if (type.layout == ADDRESS) {
      return when (value) {
        null -> MemorySegment.NULL
        is ID -> value.asMemorySegment()
        is Selector -> value.asMemorySegment()
        is MemorySegment -> value
        is Number -> {
          require(value.toLong() == 0L) { "Use ID or MemorySegment for a native address" }
          MemorySegment.NULL
        }
        is String -> {
          require(type.code == '*' || type.code == '^') { "Use nsString for an Objective-C string" }
          arena.allocateFrom(value)
        }
        is ByteArray -> arena.allocateFrom(JAVA_BYTE, *value)
        else -> error("A native pointer is required, got ${value.javaClass.name}")
      }
    }
    if (type.layout is GroupLayout) {
      val values = when (value) {
        is Foundation.NSRect -> doubleArrayOf(value.origin.x.toDouble(),
                                              value.origin.y.toDouble(),
                                              value.size.width.toDouble(),
                                              value.size.height.toDouble())
        is Foundation.NSPoint -> doubleArrayOf(value.x.toDouble(), value.y.toDouble())
        is Foundation.NSSize -> doubleArrayOf(value.width.toDouble(), value.height.toDouble())
        is CoreGraphics.CGRect -> doubleArrayOf(value.origin.x.toDouble(),
                                                value.origin.y.toDouble(),
                                                value.size.width.toDouble(),
                                                value.size.height.toDouble())
        is CoreGraphics.CGPoint -> doubleArrayOf(value.x.toDouble(), value.y.toDouble())
        is CoreGraphics.CGSize -> doubleArrayOf(value.width.toDouble(), value.height.toDouble())
        is MemorySegment -> return value
        else -> error("Unsupported native structure: ${value?.javaClass?.name}")
      }
      require(type.layout.byteSize() == values.size * JAVA_DOUBLE.byteSize()) { "Native geometry has an unexpected layout" }
      return arena.allocate(type.layout)
        .also { segment -> values.forEachIndexed { index, number -> segment.setAtIndex(JAVA_DOUBLE, index.toLong(), number) } }
    }
    val number: Number = when (value) {
      is Boolean -> if (value) 1 else 0
      is Char -> value.code
      is Number -> value
      else -> error("A native number is required, got ${value?.javaClass?.name}")
    }
    return when (type.layout) {
      JAVA_BYTE -> number.toByte()
      JAVA_SHORT -> number.toShort()
      JAVA_INT -> number.toInt()
      JAVA_LONG -> number.toLong()
      JAVA_FLOAT -> number.toFloat()
      JAVA_DOUBLE -> number.toDouble()
      else -> error("Unsupported native type: $type")
    }
  }

  @JvmStatic
  fun callback(target: MethodHandle, encoding: String, arena: Arena): MemorySegment {
    val signature = ObjcTypeEncoding(encoding).signature()
    require(target.type().parameterCount() == signature.arguments.size) { "The callback does not match $encoding" }
    require(signature.result.layout !is GroupLayout && signature.arguments.none { it.layout is GroupLayout }) {
      "Structure callbacks require an explicit native binding"
    }
    val dispatcher = MethodHandles.lookup().findStatic(
      FoundationNative::class.java, "invokeCallback",
      MethodType.methodType(Any::class.java, MethodHandle::class.java, ObjcSignature::class.java, Array<Any?>::class.java),
    )
    val bound = MethodHandles.insertArguments(dispatcher, 0, target, signature)
      .asCollector(Array<Any?>::class.java, signature.arguments.size)
      .asType(signature.descriptor.toMethodType())
    return Bindings.linker.upcallStub(bound, signature.descriptor, arena)
  }

  @JvmStatic
  private fun invokeCallback(target: MethodHandle, signature: ObjcSignature, arguments: Array<Any?>): Any? {
    try {
      val converted = arguments.mapIndexed { index, value ->
        when (target.type().parameterType(index)) {
          ID::class.java -> ID((value as MemorySegment).address())
          Selector::class.java -> Selector("native selector", (value as MemorySegment).address())
          java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> (value as Number).toByte().toInt() != 0
          String::class.java -> (value as MemorySegment).takeIf { it.address() != 0L }?.let { string(it) }
          else -> value
        }
      }
      val result = target.invokeWithArguments(converted)
      if (signature.result.layout == null) return null
      return nativeValue(signature.result, result, Arena.global())
    }
    catch (failure: Throwable) {
      try {
        logger<FoundationNative>().warn("Cannot run the native callback", failure)
      }
      catch (_: Throwable) {
      }
      return when (signature.result.layout) {
        ADDRESS -> MemorySegment.NULL
        JAVA_BYTE -> 0.toByte()
        JAVA_SHORT -> 0.toShort()
        JAVA_INT -> 0
        JAVA_LONG -> 0L
        JAVA_FLOAT -> 0F
        JAVA_DOUBLE -> 0.0
        else -> null
      }
    }
  }
}
