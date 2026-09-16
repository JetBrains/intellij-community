package com.intellij.ui.mac.touchbar

import org.assertj.core.api.Assertions.assertThat

import org.junit.jupiter.api.Test
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.MemorySegment.NULL
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BOOLEAN
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.Optional

internal class FfmNSTLibraryTest {
  @Test
  fun `strings arrays and callbacks cross the native boundary`() {
    Arena.ofConfined().use { arena ->
      val native = FakeNST(arena)
      val library = FfmNSTLibrary(native.symbols())
      var identifier = ""
      val touchbar = library.createTouchBar("Résumé", { uid -> identifier = uid; MemorySegment.ofAddress(73) }, null)
      assertThat(native.strings).isEqualTo(listOf("Résumé", null))
      val creator = Linker.nativeLinker().downcallHandle(native.arguments[1] as MemorySegment, FunctionDescriptor.of(ADDRESS, ADDRESS))
      assertThat((creator.invokeExact(arena.allocateFrom("完成")) as MemorySegment).address()).isEqualTo(73L)
      assertThat(identifier).isEqualTo("完成")
      library.selectItemsToShow(touchbar, arrayOf("first", "完成"), 2)
      assertThat(native.strings).isEqualTo(listOf("first", "完成"))
      library.setPrincipal(touchbar, "完成")
      assertThat(native.strings).isEqualTo(listOf("完成"))
      library.setTouchBar(MemorySegment.ofAddress(44), touchbar)
      assertThat(native.arguments.map { (it as MemorySegment).address() }).isEqualTo(listOf(44L, touchbar.address()))
      val group = library.createGroupItem("group", arrayOf(touchbar), 1)
      assertThat(native.handles).isEqualTo(listOf(touchbar.address()))
      library.releaseNativePeer(group)
      library.releaseNativePeer(touchbar)
      assertThat((creator.invokeExact(arena.allocateFrom("released")) as MemorySegment).address()).isEqualTo(0L)
      assertThat(native.failure).isNull()
    }
  }

  @Test
  fun `button updates reuse the callback and release detaches it`() {
    Arena.ofConfined().use { arena ->
      val native = FakeNST(arena)
      val library = FfmNSTLibrary(native.symbols())
      var calls = 0
      val button = library.createButton("button", 20, 4, "text", "hint", 1, NULL, 0, 0) { calls++ }
      assertThat(native.strings).isEqualTo(listOf("button", "text", "hint"))
      val stub = native.arguments[9] as MemorySegment
      val execute = Linker.nativeLinker().downcallHandle(stub, FunctionDescriptor.ofVoid())
      execute.invokeExact()
      library.updateButton(button, NSTLibrary.BUTTON_UPDATE_ACTION, 30, 2, null, null, 0, NULL, 0, 0) { calls += 10 }
      assertThat((native.arguments[10] as MemorySegment).address()).isEqualTo(stub.address())
      execute.invokeExact()
      library.updateButton(button, NSTLibrary.BUTTON_UPDATE_ACTION, 30, 2, null, null, 0, NULL, 0, 0, null)
      assertThat((native.arguments[10] as MemorySegment).address()).isZero()
      execute.invokeExact()
      library.setArrowImage(button, NULL, 0, 0)
      library.releaseNativePeer(button)
      execute.invokeExact()
      assertThat(calls).isEqualTo(11)
      assertThat(native.failure).isNull()
    }
  }

  @Test
  fun `scrubber queues own independent malloc buffers`() {
    Arena.ofConfined().use { arena ->
      val native = FakeNST(arena)
      val library = FfmNSTLibrary(native.symbols())
      var selected = 0
      val scrubber = library.createScrubber("scrubber", 80, { selected = it }, { 7 }, NULL, 0)
      val select = Linker.nativeLinker().downcallHandle(native.arguments[2] as MemorySegment, FunctionDescriptor.ofVoid(JAVA_INT))
      val update = Linker.nativeLinker().downcallHandle(native.arguments[3] as MemorySegment, FunctionDescriptor.of(JAVA_INT))
      select.invokeExact(42)
      assertThat(selected).isEqualTo(42)
      assertThat(update.invokeExact() as Int).isEqualTo(7)
      Arena.ofConfined().use { sourceArena ->
        val indices = sourceArena.allocateFrom(JAVA_INT, 1, 4)
        library.enableScrubberItems(scrubber, indices, 2, true)
        assertThat(native.arguments[3]).isEqualTo(true)
        indices.set(JAVA_INT, 0, 9)
        library.showScrubberItems(scrubber, indices, 2, false, true)
        assertThat(native.arguments.takeLast(2)).isEqualTo(listOf(false, true))
        library.updateScrubberItems(scrubber, indices, 8, 3)
      }
      try {
        assertThat(native.transferred.map { it.reinterpret(8).get(JAVA_INT, 0) }).isEqualTo(listOf(1, 9, 9))
        assertThat(native.transferred.map { it.reinterpret(8).get(JAVA_INT, 4) }).isEqualTo(listOf(4, 4, 4))
      }
      finally {
        val linker = Linker.nativeLinker()
        val free = linker.downcallHandle(linker.defaultLookup().findOrThrow("free"), FunctionDescriptor.ofVoid(ADDRESS))
        for (memory in native.transferred) free.invokeExact(memory)
        library.releaseNativePeer(scrubber)
      }
      assertThat(update.invokeExact() as Int).isEqualTo(0)
      assertThat(native.failure).isNull()
    }
  }

  private class FakeNST(private val arena: Arena) {
    var arguments = emptyList<Any?>()
    var strings = emptyList<String?>()
    var handles = emptyList<Long>()
    val transferred = ArrayList<MemorySegment>()
    var failure: Throwable? = null
    private var nextHandle = 100L

    fun symbols(): SymbolLookup {
      val descriptors = mapOf(
        "createTouchBar" to FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS),
        "setTouchBar" to FunctionDescriptor.ofVoid(ADDRESS, ADDRESS),
        "selectItemsToShow" to FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT),
        "setPrincipal" to FunctionDescriptor.ofVoid(ADDRESS, ADDRESS),
        "releaseNativePeer" to FunctionDescriptor.ofVoid(ADDRESS),
        "createButton" to FunctionDescriptor.of(
          ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS
        ),
        "updateButton" to FunctionDescriptor.ofVoid(
          ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS
        ),
        "createScrubber" to FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT),
        "createGroupItem" to FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT),
        "enableScrubberItems" to FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_BOOLEAN),
        "showScrubberItems" to FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_BOOLEAN, JAVA_BOOLEAN),
        "updateScrubberItems" to FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT),
        "setArrowImage" to FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT),
      )
      val dispatch = MethodHandles.lookup().findVirtual(
        javaClass, "dispatch", MethodType.methodType(Any::class.java, String::class.java, Array<Any?>::class.java)
      ).bindTo(this)
      val symbols = descriptors.mapValues { (name, descriptor) ->
        val handle = MethodHandles.insertArguments(dispatch, 0, name)
          .asCollector(Array<Any?>::class.java, descriptor.argumentLayouts().size).asType(descriptor.toMethodType())
        Linker.nativeLinker().upcallStub(handle, descriptor, arena)
      }
      return SymbolLookup { Optional.ofNullable(symbols[it]) }
    }

    fun dispatch(name: String, values: Array<Any?>): Any? {
      try {
        arguments = values.toList()
        fun string(index: Int): String? = (values[index] as MemorySegment).let {
          if (it.address() == 0L) null else it.reinterpret(Long.MAX_VALUE).getString(0)
        }
        when (name) {
          "createTouchBar" -> strings = listOf(string(0), string(2))
          "createButton" -> strings = listOf(string(0), string(3), string(4))
          "setPrincipal" -> strings = listOf(string(1))
          "selectItemsToShow", "createGroupItem" -> {
            val count = values[2] as Int
            val array = (values[1] as MemorySegment).reinterpret(count * ADDRESS.byteSize())
            handles = (0 until count).map { array.getAtIndex(ADDRESS, it.toLong()).address() }
            if (name == "selectItemsToShow") strings = handles.map { MemorySegment.ofAddress(it).reinterpret(Long.MAX_VALUE).getString(0) }
          }
          "enableScrubberItems", "showScrubberItems", "updateScrubberItems" -> transferred.add(values[1] as MemorySegment)
        }
      }
      catch (error: Throwable) {
        failure = error
      }
      return if (name.startsWith("create")) MemorySegment.ofAddress(nextHandle++) else null
    }
  }
}
