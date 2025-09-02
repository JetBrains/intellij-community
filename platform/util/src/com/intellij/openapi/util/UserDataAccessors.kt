// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Creates a property delegate for accessing data stored in [UserDataHolder].
 * The name of the property does not have to be unique: the key for the user data will be associated with the property. 
 * 
 * ##### Usage example:
 * ```
 * internal var PsiFile.treeDepthLimitExceeded: Boolean? by userData()
 * ```
 */
@ApiStatus.Experimental
inline fun <reified H : UserDataHolder, T : Any> userData(): ReadWriteProperty<H, T?> {
  return createUserDataProperty(containingClass = MethodHandles.lookup().lookupClass())
}

/**
 * Creates a property delegate for accessing data stored in [UserDataHolder]. 
 * [initializer] will be used to lazily initialize the user data if it's read before being written.
 * [initializer] can be called several times on concurrent access, but only one computed value will be stored and returned by the property.
 *
 * The name of the property does not have to be unique: the key for the user data will be associated with the property.
 * ##### Usage example:
 * ```
 * internal var PsiFile.processedClasses: ConcurrentMap<PsiClass, Boolean> 
 *   by userData { ContainerUtil.createConcurrentWeakMap() }
 * ```
 */
@ApiStatus.Experimental
inline fun <reified H : UserDataHolderEx, T : Any> userData(noinline initializer: (H) -> T): ReadWriteProperty<H, T> {
  return createUserDataProperty(containingClass = MethodHandles.lookup().lookupClass(), initializer = initializer)
}

@ApiStatus.Experimental
@PublishedApi
internal fun <H : UserDataHolder, T : Any> createUserDataProperty(containingClass: Class<*>): ReadWriteProperty<H, T?> {
  return UserDataReadWritePropertyImpl(containingClass)
}

@ApiStatus.Experimental
@PublishedApi
internal fun <H : UserDataHolderEx, T : Any> createUserDataProperty(containingClass: Class<*>, initializer: ((H) -> T)): ReadWriteProperty<H, T> {
  return UserDataReadWritePropertyWithInitializer(containingClass, initializer)
}

private open class UserDataReadWritePropertyBase<H : UserDataHolder, T>(private val containingClass: Class<*>) {
  @Volatile
  private var key: Key<T>? = null
  
  protected fun getKey(property: KProperty<*>): Key<T> {
    if (key == null) {
      synchronized(this) {
        if (key == null) {
          key = Key.create(containingClass.name + "::" + property.name)
        }
      }
    }
    return key as Key<T>
  }
}

private class UserDataReadWritePropertyImpl<H : UserDataHolder, T : Any>(containingClass: Class<*>) :
  UserDataReadWritePropertyBase<H, T>(containingClass), ReadWriteProperty<H, T?> {
    
  override fun getValue(thisRef: H, property: KProperty<*>): T? {
    return thisRef.getUserData(getKey(property))
  }

  override fun setValue(thisRef: H, property: KProperty<*>, value: T?) {
    thisRef.putUserData(getKey(property), value)
  }
}

private class UserDataReadWritePropertyWithInitializer<H : UserDataHolderEx, T : Any>(
  containingClass: Class<*>,
  private val initializer: (H) -> T
) : UserDataReadWritePropertyBase<H, T>(containingClass), ReadWriteProperty<H, T> {
  
  override fun getValue(thisRef: H, property: KProperty<*>): T {
    return thisRef.getOrCreateUserData(key = getKey(property), producer = { initializer(thisRef) })
  }

  override fun setValue(thisRef: H, property: KProperty<*>, value: T) {
    thisRef.putUserData(getKey(property), value)
  }
}