package com.jetbrains.plugin.blockmap

import java.io.*

/**
 * @param source the source for which the blockmap will be created.
 * @param algorithm the name of the algorithm requested. See the MessageDigest java class.
 */
class BlockMap(
  source: InputStream,
  private val algorithm: String = "SHA-256"
) : Serializable {
  companion object {
    private const val serialVersionUID: Long = 1234567
  }

  val chunks: List<FastCDC.Chunk> = FastCDC(source, algorithm).asSequence().toList()

  /**
   * Compare this and another blockmaps and return all chunks contained in another but not contained in this.
   */
  fun compare(another: BlockMap): List<FastCDC.Chunk> {
    val oldSet = chunks.toSet()
    return another.chunks.filter { chunk -> !oldSet.contains(chunk) }.toList()
  }

}











