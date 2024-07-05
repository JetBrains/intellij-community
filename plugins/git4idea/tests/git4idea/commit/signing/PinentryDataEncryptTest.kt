// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.net.NetUtils
import git4idea.gpg.CryptoUtils
import kotlin.random.Random

class PinentryDataEncryptTest : UsefulTestCase() {

  fun `test encrypt and decrypt`() {
    val password = PinentryTestUtil.generatePassword(Random.nextInt(2, 200))
    val keyPair = CryptoUtils.generateKeyPair()

    val encryptedPassword = CryptoUtils.encrypt(password, keyPair.private)
    val decryptedPassword = CryptoUtils.decrypt(encryptedPassword, keyPair.public)

    assertEquals(password, decryptedPassword)
  }

  fun `test public key serialization`() {
    val publicKey = CryptoUtils.generateKeyPair().public
    val address = PinentryService.Address(NetUtils.getLocalHostString(), NetUtils.findAvailableSocketPort())
    val pinentryData = PinentryService.PinentryData(CryptoUtils.publicKeyToString(publicKey), address).toString()

    val keyToDeserialize = pinentryData.split(':')[0]
    val deserializedKey = CryptoUtils.stringToPublicKey(keyToDeserialize)

    assertEquals(publicKey, deserializedKey)
  }
}
