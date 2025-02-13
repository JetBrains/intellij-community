package com.intellij.openapi.editor.impl.ad.common

import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.Entity
import fleet.kernel.*
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
internal fun <T> ChangeScope.maybeShared(entity: Entity, f: SharedChangeScope.() -> T): T {
  return if (entity.isShared) shared(f)
  else unshared(f)
}

@Experimental
internal fun <T> ChangeScope.maybeSharedRead(doc: DocumentEntity, f: SharedChangeScope.() -> T): ReplayingValue<T> {
  val box = ReplayingValue<T>()
  maybeShared(doc) {
    box.value = f()
  }
  return box
}
