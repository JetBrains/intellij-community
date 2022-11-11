// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.containers.HashSetInterner
import com.intellij.util.containers.Interner
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentBTreeEnumerator
import com.intellij.util.io.PersistentEnumeratorBase
import com.intellij.util.io.storage.AbstractStorage
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserRegistry
import com.intellij.vcs.log.impl.VcsUserImpl
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class VcsUserRegistryImpl internal constructor(project: Project) : Disposable, VcsUserRegistry {
  private val _persistentEnumerator = AtomicReference<PersistentEnumeratorBase<VcsUser>?>()
  private val persistentEnumerator: PersistentEnumeratorBase<VcsUser>?
    get() = _persistentEnumerator.get()
  private val interner: Interner<VcsUser>
  private val mapFile = File(USER_CACHE_APP_DIR, project.locationHash + "." + STORAGE_VERSION)

  init {
    initEnumerator()
    interner = HashSetInterner()
  }

  private fun initEnumerator(): Boolean {
    try {
      val enumerator = IOUtil.openCleanOrResetBroken({
                                                       PersistentBTreeEnumerator(mapFile.toPath(), VcsUserKeyDescriptor(this),
                                                                                 AbstractStorage.PAGE_SIZE, null, STORAGE_VERSION)
                                                     }, mapFile)
      val wasSet = _persistentEnumerator.compareAndSet(null, enumerator)
      if (!wasSet) {
        LOG.error("Could not assign newly opened enumerator")
        enumerator?.close()
      }
      return wasSet
    }
    catch (e: IOException) {
      LOG.warn(e)
    }
    return false
  }

  override fun createUser(name: String, email: String): VcsUser {
    synchronized(interner) {
      return interner.intern(VcsUserImpl(name, email))
    }
  }

  fun addUser(user: VcsUser) {
    try {
      persistentEnumerator?.enumerate(user)
    }
    catch (e: IOException) {
      LOG.warn(e)
      rebuild()
    }
  }

  fun addUsers(users: Collection<VcsUser>) {
    for (user in users) {
      addUser(user)
    }
  }

  override fun getUsers(): Set<VcsUser> {
    return try {
      persistentEnumerator?.getAllDataObjects { true }?.filterNotNullTo(mutableSetOf()) ?: emptySet()
    }
    catch (e: IOException) {
      LOG.warn(e)
      rebuild()
      emptySet()
    }
    catch (pce: ProcessCanceledException) {
      throw pce
    }
    catch (t: Throwable) {
      LOG.error(t)
      emptySet()
    }
  }

  fun all(condition: (t: VcsUser) -> Boolean): Boolean {
    return try {
      persistentEnumerator?.iterateData(condition) ?: false
    }
    catch (e: IOException) {
      LOG.warn(e)
      rebuild()
      false
    }
    catch (pce: ProcessCanceledException) {
      throw pce
    }
    catch (t: Throwable) {
      LOG.error(t)
      false
    }
  }

  private fun rebuild() {
    if (persistentEnumerator?.isCorrupted == true) {
      _persistentEnumerator.getAndSet(null)?.let { oldEnumerator ->
        ApplicationManager.getApplication().executeOnPooledThread {
          try {
            oldEnumerator.close()
          }
          catch (_: IOException) {
          }
          finally {
            initEnumerator()
          }
        }
      }
    }
  }

  fun flush() {
    persistentEnumerator?.force()
  }

  override fun dispose() {
    try {
      persistentEnumerator?.close()
    }
    catch (e: IOException) {
      LOG.warn(e)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(VcsUserRegistryImpl::class.java)
    private val USER_CACHE_APP_DIR = File(PathManager.getSystemPath(), "vcs-users")
    private const val STORAGE_VERSION = 2
  }
}

class VcsUserKeyDescriptor(private val userRegistry: VcsUserRegistry) : KeyDescriptor<VcsUser> {
  @Throws(IOException::class)
  override fun save(out: DataOutput, value: VcsUser) {
    IOUtil.writeUTF(out, value.name)
    IOUtil.writeUTF(out, value.email)
  }

  @Throws(IOException::class)
  override fun read(`in`: DataInput): VcsUser {
    val name = IOUtil.readUTF(`in`)
    val email = IOUtil.readUTF(`in`)
    return userRegistry.createUser(name, email)
  }

  override fun getHashCode(value: VcsUser): Int {
    return value.hashCode()
  }

  override fun isEqual(val1: VcsUser?, val2: VcsUser?): Boolean {
    return val1 == val2
  }
}
