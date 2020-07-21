// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.plugin.blockmap

import com.intellij.openapi.progress.ProgressIndicator
import java.io.*


interface ChunkDataSource{
  fun get(chunk : FastCDC.Chunk) : ByteArray?
}

open class Merger(
  private val oldFile : File,
  private val oldBlockMap : BlockMap = BlockMap(oldFile.inputStream()),
  private val newBlockMap: BlockMap,
  private val bufferSize : Int = 64*1024
){
  private val buffer = ByteArray(bufferSize)

  open fun merge(output : OutputStream, newChunkDataSource : ChunkDataSource) {
    RandomAccessFile(oldFile, "r").use { prevFileRAF ->
      output.buffered().use { bufferedOutput ->
        val oldMap = oldBlockMap.chunks.associateBy { it.hash }
        for (newChunk in newBlockMap.chunks) {
          val oldChunk = oldMap[newChunk.hash]
          if (oldChunk != null) downloadChunkFromOldData(oldChunk, prevFileRAF, bufferedOutput)
          else downloadChunkFromNewData(newChunk, newChunkDataSource, bufferedOutput)
        }
      }
    }
  }

  open fun downloadChunkFromOldData(oldChunk : FastCDC.Chunk, prevFileRAF : RandomAccessFile,
                                     output : OutputStream){
    prevFileRAF.seek(oldChunk.offset.toLong())
    var remainingBytes = oldChunk.length
    while (remainingBytes != 0){
      val length = if(remainingBytes >= bufferSize) bufferSize else remainingBytes
      prevFileRAF.read(buffer, 0, length)
      for (i in 0 until length) {
        output.write(buffer[i].toInt())
      }
      remainingBytes-=length
    }
  }

  open fun downloadChunkFromNewData(newChunk : FastCDC.Chunk, newChunkDataSource: ChunkDataSource,
                                    output : OutputStream){
      val chunkData = newChunkDataSource.get(newChunk)
      if(chunkData == null) throw Exception()
      if(chunkData.size == newChunk.length){
        output.write(chunkData)
      }else throw Exception()
  }
}

class IdeaMerger(
  private val oldFile : File,
  private val oldBlockMap : BlockMap = BlockMap(oldFile.inputStream()),
  private val newBlockMap: BlockMap,
  private val bufferSize : Int = 64*1024,
  private val indicator : ProgressIndicator
) : Merger(oldFile, oldBlockMap, newBlockMap, bufferSize) {
  private val newFileSize : Int = newBlockMap.chunks.stream().mapToInt { e -> e.length }.sum()
  private var wroteBytes : Int = 0

  override fun merge(output: OutputStream, newChunkDataSource: ChunkDataSource) {
    indicator.checkCanceled()
    indicator.isIndeterminate = newFileSize <= 0
    super.merge(output, newChunkDataSource)
  }

  override fun downloadChunkFromNewData(newChunk: FastCDC.Chunk, newChunkDataSource: ChunkDataSource,
                                        output: OutputStream) {
    super.downloadChunkFromNewData(newChunk, newChunkDataSource, output)
    wroteBytes+=newChunk.length
    setIndicatorFraction()
  }

  override fun downloadChunkFromOldData(oldChunk: FastCDC.Chunk, prevFileRAF: RandomAccessFile,
                                         output: OutputStream) {
    super.downloadChunkFromOldData(oldChunk, prevFileRAF, output)
    wroteBytes+=oldChunk.length
    setIndicatorFraction()
  }

  private fun setIndicatorFraction(){
    indicator.checkCanceled()
    indicator.fraction = wroteBytes.toDouble() / newFileSize.toDouble()
  }
}