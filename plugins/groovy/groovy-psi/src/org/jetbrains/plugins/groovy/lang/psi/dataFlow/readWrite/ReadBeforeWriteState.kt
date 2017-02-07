/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.dataFlow.readWrite

import java.util.*

class ReadBeforeWriteState(
  val writes: BitSet = BitSet(),
  val reads: BitSet = BitSet()
) : Cloneable {

  public override fun clone() = ReadBeforeWriteState(writes.clone() as BitSet, reads.clone() as BitSet)

  override fun toString() = "(writes=$writes, reads=$reads)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other?.javaClass != javaClass) return false
    other as ReadBeforeWriteState
    return writes == other.writes && reads == other.reads
  }

  override fun hashCode(): Int {
    return 31 * writes.hashCode() + reads.hashCode()
  }
}