/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Interner
import com.intellij.util.io.*
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserRegistry
import com.intellij.vcs.log.impl.VcsUserImpl
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 *
 */
class VcsUserRegistryImpl internal constructor(project: Project) : Disposable, VcsUserRegistry {
  private val _persistentEnumerator = AtomicReference<PersistentEnumeratorBase<VcsUser>?>()
  private val persistentEnumerator: PersistentEnumeratorBase<VcsUser>?
    get() = _persistentEnumerator.get()
  private val interner: Interner<VcsUser>
  private val mapFile = File(USER_CACHE_APP_DIR, project.locationHash + "." + STORAGE_VERSION)

  init {
    initEnumerator()
    interner = Interner()
  }

  private fun initEnumerator(): Boolean {
    try {
      val enumerator = IOUtil.openCleanOrResetBroken({
                                                       PersistentBTreeEnumerator(mapFile, VcsUserKeyDescriptor(this), Page.PAGE_SIZE, null,
                                                                                 STORAGE_VERSION)
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
      persistentEnumerator?.let { ContainerUtil.newHashSet(it.getAllDataObjects { true }) } ?: emptySet()
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

  override fun isEqual(val1: VcsUser, val2: VcsUser): Boolean {
    return val1 == val2
  }
}
