package org.jetbrains.deft.impl.fields


data class ExtFieldId(val localId: Int) {
  override fun toString(): String = "$localId"
}