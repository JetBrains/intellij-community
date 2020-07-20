// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.plugin.blockmap

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.util.io.HttpRequests
import java.io.*


data class ChunkData(val hash : String, val data : ByteArray)

open class Merger(
  private val oldFile : File,
  private val oldBlockMap : BlockMap = BlockMap(oldFile.inputStream()),
  private val newBlockMap: BlockMap
){


  fun merge(output : OutputStream, newChunkDataIterator : Iterator<ChunkData>){
    val prevFileRAF = RandomAccessFile(oldFile, "r")
    val bufferedOutput = output.buffered()
    val oldMap = oldBlockMap.chunks.associateBy { it.hash }
    val rangeBytes = StringBuilder()
    var rangeCnt = 0
    var curChunk = 0
    val buffer = ByteArray(64 * 1024)
    var summaryDownloadedBytes = 0
    for (newChunk in newBlockMap.chunks) {
      if (!oldMap.containsKey(newChunk.hash)) {
        rangeBytes.append("${newChunk.offset}-${newChunk.offset + newChunk.length - 1},")
        rangeCnt++
        summaryDownloadedBytes+=newChunk.length
      }
      while(curChunk < newBlockMap.chunks.size) {
        val chunk = newBlockMap.chunks[curChunk]
        val oldChunk = oldMap[chunk.hash]
        if (oldChunk != null) {
          downloadChunkFromOldData(oldChunk, prevFileRAF, buffer, bufferedOutput)
        }
        else {
          downloadChunkFromNewData(newChunk, newChunkDataIterator, bufferedOutput)
        }
        curChunk++
      }



      rangeCnt = 0
      rangeBytes.clear()


    }
  }


  open fun downloadChunkFromOldData(oldChunk : FastCDC.Chunk, prevFileRAF : RandomAccessFile,
                                    buffer : ByteArray, output : OutputStream){
    prevFileRAF.seek(oldChunk.offset.toLong())
    prevFileRAF.read(buffer, 0, oldChunk.length)
    for (i in 0 until oldChunk.length) {
      output.write(buffer[i].toInt())
    }
  }

  open fun downloadChunkFromNewData(newChunk : FastCDC.Chunk, newChunkDataIterator: Iterator<ChunkData>,
                                    output : OutputStream){
    if(newChunkDataIterator.hasNext()){
      val chunkData = newChunkDataIterator.next()
      if(chunkData.hash == newChunk.hash && chunkData.data.size == newChunk.length){
        output.write(chunkData.data)
      }else throw Exception()
    }else throw Exception()
  }



}