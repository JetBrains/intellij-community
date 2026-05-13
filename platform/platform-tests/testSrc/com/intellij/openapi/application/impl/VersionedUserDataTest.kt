// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.Key
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersionRegistry
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.PsiVersioningLockingListener
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.keyFMap.ArrayBackedFMap
import com.intellij.util.keyFMap.KeyFMap
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestApplication
@RegistryKey(key = "psi.enable.persistent.syntax.tree", value = "true")
internal class VersionedUserDataTest {

  @Test
  fun `versioned user data operations use lower bound across psi versions`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val key = Key.create<UserDataPayload>("versioned user data test")
    val initial = UserDataPayload("initial")
    val ignored = UserDataPayload("ignored")
    val replacement = UserDataPayload("replacement")
    val later = UserDataPayload("later")
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      assertSame(initial, leaf.putUserDataIfAbsent(key, initial))
      assertSame(initial, leaf.putUserDataIfAbsent(key, ignored))
      assertTrue(leaf.replace(key, initial, replacement))
      assertSame(replacement, leaf.getUserData(key))
    }

    advancePsiVersion()

    PsiVersioningService.freezePsiVersion {
      assertSame(replacement, leaf.getUserData(key))
      assertSame(replacement, leaf.putUserDataIfAbsent(key, later))
      assertTrue(leaf.replace(key, replacement, later))
      assertFalse(leaf.replace(key, replacement, ignored))
      assertSame(later, leaf.getUserData(key))
    }
  }

  @Test
  fun `versioned user data null value removes inherited value until restored`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val key = Key.create<UserDataPayload>("versioned user data removal test")
    val initial = UserDataPayload("initial")
    val restored = UserDataPayload("restored")
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      leaf.putUserData(key, initial)
    }

    advancePsiVersion()

    PsiVersioningService.freezePsiVersion {
      assertSame(initial, leaf.getUserData(key))
      leaf.putUserData(key, null)
      assertNull(leaf.getUserData(key))
    }

    advancePsiVersion()

    PsiVersioningService.freezePsiVersion {
      assertNull(leaf.getUserData(key))
      assertSame(restored, leaf.putUserDataIfAbsent(key, restored))
      assertSame(restored, leaf.getUserData(key))
    }
  }

  @Test
  fun `versioned user data modification attempts compact stale history`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val plusKey = Key.create<UserDataPayload>("versioned user data plus cleanup test")
    val minusKey = Key.create<UserDataPayload>("versioned user data minus cleanup test")
    val plusInitial = UserDataPayload("plusInitial")
    val plusReplacement = UserDataPayload("plusReplacement")
    val minusInitial = UserDataPayload("minusInitial")
    val plusLeaf = createVersionedLeaf()
    val minusLeaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      plusLeaf.putUserData(plusKey, plusInitial)
      minusLeaf.putUserData(minusKey, minusInitial)
    }

    retainCurrentPsiVersion {
      advancePsiVersion()
      PsiVersioningService.freezePsiVersion {
        plusLeaf.putUserData(plusKey, plusReplacement)
        minusLeaf.putUserData(minusKey, null)
      }
      assertReferenced(plusLeaf, plusInitial)
      assertReferenced(minusLeaf, minusInitial)
    }

    PsiVersioningService.freezePsiVersion {
      plusLeaf.putUserData(plusKey, plusReplacement)
      assertSame(plusReplacement, plusLeaf.getUserData(plusKey))
    }
    assertNotReferenced(plusLeaf, plusInitial)

    PsiVersioningService.freezePsiVersion {
      minusLeaf.putUserData(minusKey, null)
      assertNull(minusLeaf.getUserData(minusKey))
    }
    assertNotReferenced(minusLeaf, minusInitial)
  }

  @Test
  fun `versioned user data maps compare full storage instead of current snapshot`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val key = Key.create<UserDataPayload>("versioned user data equality test")
    val oldValue = UserDataPayload("oldValue")
    val currentValue = UserDataPayload("currentValue")
    val leafWithHistory = createVersionedLeaf()
    val leafWithoutHistory = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      leafWithHistory.putUserData(key, oldValue)
    }

    retainCurrentPsiVersion {
      advancePsiVersion()
      PsiVersioningService.freezePsiVersion {
        leafWithHistory.putUserData(key, currentValue)
        leafWithoutHistory.putUserData(key, currentValue)

        assertEquals(leafWithHistory.getUserData(key), leafWithoutHistory.getUserData(key))
        assertNotEquals(leafWithHistory.userMap, leafWithoutHistory.userMap)
      }
    }
  }

  @Test
  fun `copyable user data is copied to clone and ordinary user data is cleared`() {
    val ordinaryKey = Key.create<UserDataPayload>("ordinary clone data")
    val copyableKey = Key.create<UserDataPayload>("copyable clone data")
    val ordinary = UserDataPayload("ordinary")
    val copyable = UserDataPayload("copyable")
    val leaf = createVersionedLeaf()

    leaf.putUserData(ordinaryKey, ordinary)
    leaf.putCopyableUserData(copyableKey, copyable)

    val clone = leaf.clone() as ClearableLeafPsiElement

    assertNull(clone.getUserData(ordinaryKey))
    assertSame(copyable, clone.getCopyableUserData(copyableKey))
  }

  @Test
  fun `versioned user data map uses space-optimized flavors for small key counts`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val keys = List(4) { Key.create<UserDataPayload>("versioned user data flavor test $it") }
    val values = List(4) { UserDataPayload("flavor value $it") }
    val leaf = createVersionedLeaf()

    assertEquals("VersionedUserDataFMap0", leaf.userMap.javaClass.simpleName)

    PsiVersioningService.freezePsiVersion {
      val expectedFlavors = listOf("VersionedUserDataFMap1", "VersionedUserDataFMap2",
                                   "ArrayVersionedUserDataFMap", "ArrayVersionedUserDataFMap")
      for (i in keys.indices) {
        leaf.putUserData(keys[i], values[i])
        assertEquals(expectedFlavors[i], leaf.userMap.javaClass.simpleName)
      }

      // a removal within the version that installed the value leaves no history behind, so the storage shrinks back
      val expectedFlavorsAfterRemoval = listOf("ArrayVersionedUserDataFMap", "VersionedUserDataFMap2",
                                               "VersionedUserDataFMap1", "VersionedUserDataFMap0")
      for (i in keys.indices) {
        leaf.putUserData(keys[i], null)
        assertEquals(expectedFlavorsAfterRemoval[i], leaf.userMap.javaClass.simpleName)
      }
    }
  }

  @Test
  fun `versioned user data map switches to the map-backed flavor for large key counts`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    // the array-backed flavor holds up to ArrayBackedFMap.ARRAY_THRESHOLD keys, the map-backed one takes over past it
    val keys = List(10) { Key.create<UserDataPayload>("versioned user data map flavor test $it") }
    val values = List(10) { UserDataPayload("map flavor value $it") }
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      for (i in keys.indices) {
        leaf.putUserData(keys[i], values[i])
        assertEquals(flavorForStorageSize(i + 1), leaf.userMap.javaClass.simpleName, "unexpected flavor after ${i + 1} keys")
      }
      assertEquals(10, leaf.userMap.size())
      assertEquals(keys, leaf.userMap.keys.toList())
      for (i in keys.indices) {
        assertSame(values[i], leaf.getUserData(keys[i]))
      }

      // a removal within the version that installed the value leaves no history behind, so the storage shrinks back
      for (i in keys.indices.reversed()) {
        leaf.putUserData(keys[i], null)
        assertEquals(flavorForStorageSize(i), leaf.userMap.javaClass.simpleName, "unexpected flavor after dropping down to $i keys")
      }
    }
  }

  @Test
  fun `map-backed versioned user data map hides keys that are invisible for the current version`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val keys = List(10) { Key.create<UserDataPayload>("versioned user data map visibility test $it") }
    val values = List(10) { UserDataPayload("map visibility value $it") }
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      for (i in keys.indices) {
        leaf.putUserData(keys[i], values[i])
      }
    }

    retainCurrentPsiVersion {
      advancePsiVersion()
      PsiVersioningService.freezePsiVersion {
        for (i in 0 until 4) {
          leaf.putUserData(keys[i], null)
        }

        val map = leaf.userMap
        // the earlier version is still alive, hence the removed keys have to stay in the storage
        assertEquals("MapVersionedUserDataFMap", map.javaClass.simpleName)
        assertEquals(6, map.size())
        assertEquals(keys.drop(4), map.keys.toList())
        assertFalse(map.isEmpty)
        for (i in 0 until 4) {
          assertNull(leaf.getUserData(keys[i]))
        }
        for (i in 4 until keys.size) {
          assertSame(values[i], leaf.getUserData(keys[i]))
        }
      }
    }
  }

  @Test
  fun `map-backed versioned user data map matches plain key maps for the current version`(
    @TestDisposable disposable: Disposable,
  ) {
    installVersioningListeners(disposable)

    val keys = List(10) { Key.create<UserDataPayload>("versioned user data map slice test $it") }
    val values = List(10) { UserDataPayload("map slice value $it") }
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      var plainMap: KeyFMap = KeyFMap.EMPTY_MAP
      for (i in keys.indices) {
        leaf.putUserData(keys[i], values[i])
        plainMap = plainMap.plus(keys[i], values[i])

        val versionedMap = leaf.userMap
        assertEquals(plainMap.size(), versionedMap.size())
        // `MapBackedFMap` reports its keys in the hash table order, while the versioned storage is always sorted
        assertEquals(plainMap.keys.sortedBy { it.hashCode() }, versionedMap.keys.sortedBy { it.hashCode() })
        assertTrue(versionedMap.equalsByReference(plainMap))
      }
    }
  }

  @Test
  fun `versioned user data map hides keys that are invisible for the current version`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val removedKey = Key.create<UserDataPayload>("versioned user data invisible key test")
    val retainedKey = Key.create<UserDataPayload>("versioned user data visible key test")
    val removed = UserDataPayload("removed")
    val retained = UserDataPayload("retained")
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      leaf.putUserData(removedKey, removed)
      leaf.putUserData(retainedKey, retained)
    }

    retainCurrentPsiVersion {
      advancePsiVersion()
      PsiVersioningService.freezePsiVersion {
        leaf.putUserData(removedKey, null)

        val map = leaf.userMap
        // the earlier version is still alive, hence the removed key has to stay in the storage
        assertEquals("VersionedUserDataFMap2", map.javaClass.simpleName)
        assertEquals(1, map.size())
        assertEquals(listOf(retainedKey), map.keys.toList())
        assertFalse(map.isEmpty)
        assertNull(leaf.getUserData(removedKey))
      }
    }
  }

  @Test
  fun `versioned user data map matches plain key maps for the current version`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val keys = List(3) { Key.create<UserDataPayload>("versioned user data slice test $it") }
    val values = List(3) { UserDataPayload("slice value $it") }
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      var plainMap: KeyFMap = KeyFMap.EMPTY_MAP
      for (i in keys.indices) {
        leaf.putUserData(keys[i], values[i])
        plainMap = plainMap.plus(keys[i], values[i])

        val versionedMap = leaf.userMap
        assertEquals(plainMap.size(), versionedMap.size())
        assertEquals(plainMap.keys.toList(), versionedMap.keys.toList())
        assertEquals(plainMap.valueIdentityHashCode, versionedMap.valueIdentityHashCode)
        assertEquals(plainMap.toString(), versionedMap.toString())
        assertTrue(versionedMap.equalsByReference(plainMap))
      }
    }
  }

  @Test
  fun `versioned user data maps with equal storage have equal hash codes`(@TestDisposable disposable: Disposable) {
    installVersioningListeners(disposable)

    val retainedKey = Key.create<UserDataPayload>("versioned user data equal storage test")
    val temporaryKey = Key.create<UserDataPayload>("versioned user data temporary key test")
    val retained = UserDataPayload("retained")
    val temporary = UserDataPayload("temporary")
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      leaf.putUserData(retainedKey, retained)
      val mapWithOneKey = leaf.userMap

      leaf.putUserData(temporaryKey, temporary)
      leaf.putUserData(temporaryKey, null)
      val mapWithSameStorage = leaf.userMap

      assertNotSame(mapWithOneKey, mapWithSameStorage)
      assertEquals(mapWithOneKey, mapWithSameStorage)
      assertEquals(mapWithOneKey.hashCode(), mapWithSameStorage.hashCode())
    }
  }

  @Test
  fun `versioned user data map is not recreated when nothing changes`(
    @TestDisposable disposable: Disposable,
  ) {
    installVersioningListeners(disposable)

    val key = Key.create<UserDataPayload>("versioned user data identity test")
    val absentKey = Key.create<UserDataPayload>("versioned user data absent key test")
    val value = UserDataPayload("value")
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      leaf.putUserData(key, value)
      val map = leaf.userMap

      leaf.putUserData(key, value)
      assertSame(map, leaf.userMap)

      leaf.putUserData(absentKey, null)
      assertSame(map, leaf.userMap)
    }
  }

  @Test
  fun `versioned user data removal marker is dropped when its history becomes obsolete`(
    @TestDisposable disposable: Disposable,
  ) {
    installVersioningListeners(disposable)

    val key = Key.create<UserDataPayload>("versioned user data removal marker test")
    val initial = UserDataPayload("initial")
    val leaf = createVersionedLeaf()

    PsiVersioningService.freezePsiVersion {
      leaf.putUserData(key, initial)
    }

    retainCurrentPsiVersion {
      advancePsiVersion()
      PsiVersioningService.freezePsiVersion {
        leaf.putUserData(key, null)
      }
      assertEquals("VersionedUserDataFMap1", leaf.userMap.javaClass.simpleName)
      assertReferenced(leaf, initial)
    }

    // the version that could see the value is gone, so the removal marker does not have to be kept anymore
    PsiVersioningService.freezePsiVersion {
      leaf.putUserData(key, null)
      assertNull(leaf.getUserData(key))
    }
    assertEquals("VersionedUserDataFMap0", leaf.userMap.javaClass.simpleName)
    assertTrue(leaf.userMap.isEmpty)
    assertNotReferenced(leaf, initial)
  }

  /**
   * The flavor of [com.intellij.psi.impl.source.tree.mvcc.userData.VersionedUserDataFMap] that is expected to hold
   * the given number of keys. Mirrors the ladder of the plain `KeyFMap` implementations, `ARRAY_THRESHOLD` included.
   */
  private fun flavorForStorageSize(storageSize: Int): String = when (storageSize) {
    0 -> "VersionedUserDataFMap0"
    1 -> "VersionedUserDataFMap1"
    2 -> "VersionedUserDataFMap2"
    in 3..ArrayBackedFMap.ARRAY_THRESHOLD -> "ArrayVersionedUserDataFMap"
    else -> "MapVersionedUserDataFMap"
  }

  private fun installVersioningListeners(disposable: Disposable) {
    val listener = PsiVersioningLockingListener()
    ApplicationManagerEx.getApplicationEx().addWriteActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addWriteIntentReadActionListener(listener, disposable)
    ApplicationManagerEx.getApplicationEx().addSuspendingWriteActionListener(listener, disposable)
  }

  private fun createVersionedLeaf(): ClearableLeafPsiElement {
    return runVersionedWriteAction { ClearableLeafPsiElement("node") }
  }

  private fun advancePsiVersion() {
    runVersionedWriteAction { }
  }

  private fun <T> retainCurrentPsiVersion(action: () -> T): T {
    val registry = PsiVersionRegistry.instance
    return registry.rememberFrozenVersion(registry.latestPublishedVersion, action)
  }

  private fun <T> runVersionedWriteAction(action: () -> T): T {
    return runWriteAction {
      InternalPsiVersioning.inVersionedEnvironment(true, action)
    }
  }

  private class ClearableLeafPsiElement(private val debugName: String) : LeafPsiElement(IElementType(debugName, null), debugName) {
    override fun toString(): String {
      return debugName
    }
  }

  private class UserDataPayload(private val debugName: String) {
    override fun toString(): String {
      return debugName
    }
  }
}
