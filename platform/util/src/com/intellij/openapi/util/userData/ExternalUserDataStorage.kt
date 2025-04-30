package com.intellij.openapi.util.userData

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.util.keyFMap.KeyFMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ExternalUserDataStorage {

  fun <T : Any> getUserData(obj: UserDataHolder, key: Key<T>): T?

  fun <T : Any> putUserData(obj: UserDataHolder, key: Key<T>, value: T?)

  fun <T : Any> compareAndPutUserData(obj: UserDataHolder, key: Key<T>, oldValue: T?, newValue: T?): Boolean

  fun getUserMap(obj: UserDataHolder): KeyFMap

  fun setUserMap(obj: UserDataHolder, userMap: KeyFMap)

  fun <T : Any> putUserDataIfAbsent(obj: UserDataHolder, key: Key<T>, value: T): T
}