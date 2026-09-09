package com.intellij.ui.mac.touchbar

import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.MemorySegment.NULL
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BOOLEAN
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class FfmNSTLibrary(symbols: SymbolLookup) : NSTLibrary {
  private val linker = Linker.nativeLinker()
  private val createTouchBar = linker.downcallHandle(
    symbols.findOrThrow("createTouchBar"), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS)
  )
  private val setTouchBar = linker.downcallHandle(symbols.findOrThrow("setTouchBar"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS))
  private val selectItems = linker.downcallHandle(
    symbols.findOrThrow("selectItemsToShow"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT)
  )
  private val setPrincipal = linker.downcallHandle(symbols.findOrThrow("setPrincipal"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS))
  private val releasePeer = linker.downcallHandle(symbols.findOrThrow("releaseNativePeer"), FunctionDescriptor.ofVoid(ADDRESS))
  private val createButton = linker.downcallHandle(symbols.findOrThrow("createButton"), FunctionDescriptor.of(
    ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS
  ))
  private val updateButton = linker.downcallHandle(symbols.findOrThrow("updateButton"), FunctionDescriptor.ofVoid(
    ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS
  ))
  private val createScrubber = linker.downcallHandle(symbols.findOrThrow("createScrubber"), FunctionDescriptor.of(
    ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT
  ))
  private val createGroup = linker.downcallHandle(
    symbols.findOrThrow("createGroupItem"), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT)
  )
  private val enableItems = linker.downcallHandle(
    symbols.findOrThrow("enableScrubberItems"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_BOOLEAN)
  )
  private val showItems = linker.downcallHandle(symbols.findOrThrow("showScrubberItems"), FunctionDescriptor.ofVoid(
    ADDRESS, ADDRESS, JAVA_INT, JAVA_BOOLEAN, JAVA_BOOLEAN
  ))
  private val updateItems = linker.downcallHandle(
    symbols.findOrThrow("updateScrubberItems"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT)
  )
  private val setArrow = linker.downcallHandle(
    symbols.findOrThrow("setArrowImage"), FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT)
  )
  private val callbacks = ConcurrentHashMap<Long, List<NSTCallback>>()

  override fun createTouchBar(name: String?, creator: NSTLibrary.ItemCreator?, escId: String?): MemorySegment =
    withCallbacks(listOf(NSTCallback.creator(creator))) { stubs ->
      Arena.ofConfined().use { arena ->
        createTouchBar.invokeExact(arena.string(name), stubs[0].stub, arena.string(escId)) as MemorySegment
      }
    }

  override fun setTouchBar(nsView: MemorySegment?, tbObj: MemorySegment?) {
    setTouchBar.invokeExact(nsView ?: NULL, tbObj ?: NULL)
  }

  override fun selectItemsToShow(tbObj: MemorySegment, ids: Array<String?>?, count: Int) {
    require(count >= 0 && count <= (ids?.size ?: 0))
    Arena.ofConfined().use<Arena, Unit> { arena ->
      val pointers = if (count == 0) NULL else arena.allocate(ADDRESS, count.toLong())
      for (index in 0 until count) pointers.setAtIndex(ADDRESS, index.toLong(), arena.string(ids!![index]))
      selectItems.invokeExact(tbObj, pointers, count)
    }
  }

  override fun setPrincipal(tbObj: MemorySegment, uid: String?) {
    Arena.ofConfined().use<Arena, Unit> { arena -> setPrincipal.invokeExact(tbObj, arena.string(uid)) }
  }

  override fun releaseNativePeer(nativePeerPtr: MemorySegment) {
    callbacks.remove(nativePeerPtr.address())?.forEach { it.clear() }
    releasePeer.invokeExact(nativePeerPtr)
  }

  override fun createButton(
    uid: String?, buttWidth: Int, buttonFlags: Int, text: String?, hint: String?, isHintDisabled: Int,
    raster4ByteRGBA: MemorySegment?, width: Int, height: Int, action: NSTLibrary.Action?,
  ): MemorySegment = withCallbacks(listOf(NSTCallback.action(action))) { stubs ->
    Arena.ofConfined().use { arena ->
      createButton.invokeExact(
        arena.string(uid), buttWidth, buttonFlags, arena.string(text), arena.string(hint), isHintDisabled,
        raster4ByteRGBA ?: NULL, width, height, if (action == null) NULL else stubs[0].stub
      ) as MemorySegment
    }
  }

  override fun createScrubber(
    uid: String?, itemWidth: Int, delegate: NSTLibrary.ScrubberDelegate?, updater: NSTLibrary.ScrubberCacheUpdater?,
    packedItems: MemorySegment?, byteCount: Int,
  ): MemorySegment = withCallbacks(listOf(NSTCallback.delegate(delegate), NSTCallback.updater(updater))) { stubs ->
    Arena.ofConfined().use { arena ->
      createScrubber.invokeExact(
        arena.string(uid), itemWidth, if (delegate == null) NULL else stubs[0].stub,
        if (updater == null) NULL else stubs[1].stub, packedItems ?: NULL, byteCount
      ) as MemorySegment
    }
  }

  override fun createGroupItem(uid: String?, items: Array<MemorySegment>?, count: Int): MemorySegment {
    require(count >= 0 && count <= (items?.size ?: 0))
    return Arena.ofConfined().use { arena ->
      val pointers = if (count == 0) NULL else arena.allocate(ADDRESS, count.toLong())
      for (index in 0 until count) pointers.setAtIndex(ADDRESS, index.toLong(), items!![index])
      createGroup.invokeExact(arena.string(uid), pointers, count) as MemorySegment
    }
  }

  override fun updateButton(
    buttonObj: MemorySegment, updateOptions: Int, buttWidth: Int, buttonFlags: Int, text: String?, hint: String?,
    isHintDisabled: Int, raster4ByteRGBA: MemorySegment?, width: Int, height: Int, action: NSTLibrary.Action?,
  ) {
    val callback = callbacks[buttonObj.address()]?.singleOrNull()
    if (updateOptions and NSTLibrary.BUTTON_UPDATE_ACTION != 0) callback?.target = action
    Arena.ofConfined().use<Arena, Unit> { arena ->
      updateButton.invokeExact(
        buttonObj, updateOptions, buttWidth, buttonFlags, arena.string(text), arena.string(hint), isHintDisabled,
        raster4ByteRGBA ?: NULL, width, height, if (action == null) NULL else callback?.stub ?: NULL
      )
    }
  }

  override fun enableScrubberItems(scrubObj: MemorySegment, itemIndices: MemorySegment?, count: Int, enabled: Boolean) {
    if (count <= 0 || itemIndices == null || itemIndices.address() == 0L) return
    transfer(itemIndices, Math.multiplyExact(count.toLong(), Integer.BYTES.toLong())) { memory ->
      enableItems.invokeExact(scrubObj, memory, count, enabled)
    }
  }

  override fun showScrubberItems(scrubObj: MemorySegment, itemIndices: MemorySegment?, count: Int, show: Boolean, inverseOthers: Boolean) {
    transfer(itemIndices, Math.multiplyExact(count.toLong(), Integer.BYTES.toLong())) { memory ->
      showItems.invokeExact(scrubObj, memory, count, show, inverseOthers)
    }
  }

  override fun updateScrubberItems(scrubObj: MemorySegment, packedItems: MemorySegment?, byteCount: Int, fromIndex: Int) {
    transfer(packedItems, byteCount.toLong()) { memory -> updateItems.invokeExact(scrubObj, memory, byteCount, fromIndex) }
  }

  override fun setArrowImage(buttObj: MemorySegment, raster4ByteRGBA: MemorySegment?, width: Int, height: Int) {
    setArrow.invokeExact(buttObj, raster4ByteRGBA ?: NULL, width, height)
  }

  private inline fun withCallbacks(stubs: List<NSTCallback>, create: (List<NSTCallback>) -> MemorySegment): MemorySegment {
    try {
      val peer = create(stubs)
      if (peer.address() == 0L) stubs.forEach { it.clear() }
      else callbacks[peer.address()] = stubs
      return peer
    }
    catch (error: Throwable) {
      stubs.forEach { it.clear() }
      throw error
    }
  }

  private fun Arena.string(value: String?): MemorySegment = if (value == null) NULL else allocateFrom(value)

  private object Allocator {
    private val linker = Linker.nativeLinker()
    val malloc = linker.downcallHandle(linker.defaultLookup().findOrThrow("malloc"), FunctionDescriptor.of(ADDRESS, JAVA_LONG))
    val free = linker.downcallHandle(linker.defaultLookup().findOrThrow("free"), FunctionDescriptor.ofVoid(ADDRESS))
  }

  /** Transfers a malloc buffer to the native queue, which releases it with free. */
  private inline fun transfer(source: MemorySegment?, size: Long, action: (MemorySegment) -> Unit) {
    require(size >= 0)
    if (source == null || source.address() == 0L || size == 0L) {
      action(NULL)
      return
    }
    require(size <= source.byteSize())
    val memory = Allocator.malloc.invokeExact(size) as MemorySegment
    if (memory.address() == 0L) throw OutOfMemoryError("Cannot allocate a Touch Bar buffer")
    try {
      MemorySegment.copy(source, 0, memory.reinterpret(size), 0, size)
      action(memory)
    }
    catch (error: Throwable) {
      Allocator.free.invokeExact(memory)
      throw error
    }
  }
}
