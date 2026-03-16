// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.zombie

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readLONG
import com.intellij.util.io.DataInputOutputUtil.writeLONG
import java.io.DataInput
import java.io.DataOutput


data class FingerprintedZombieImpl<Z : Zombie>(
  private val fingerprint: Long,
  private val zombie: Z,
) : FingerprintedZombie<Z> {

  override fun fingerprint(): Long = fingerprint
  override fun zombie(): Z = zombie

  companion object {
    fun captureFingerprint(documentContent: CharSequence): Long {
      return Hashing.komihash5_0().hashCharsToLong(documentContent)
    }
  }

  class Externalizer<Z : Zombie>(
    private val necromancy: Necromancy<Z>,
  ) : DataExternalizer<FingerprintedZombie<Z>> {

    override fun read(input: DataInput): FingerprintedZombie<Z> {
      val fingerprint = readLONG(input)
      val zombie = necromancy.exhumeZombie(input)
      return FingerprintedZombieImpl(fingerprint, zombie)
    }

    override fun save(output: DataOutput, value: FingerprintedZombie<Z>) {
      writeLONG(output, value.fingerprint())
      necromancy.buryZombie(output, value.zombie())
    }
  }
}
