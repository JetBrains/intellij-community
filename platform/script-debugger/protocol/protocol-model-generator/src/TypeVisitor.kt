package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel

interface TypeVisitor<R> {
  public fun visitRef(refName: String): R

  public fun visitBoolean(): R

  public fun visitEnum(enumConstants: List<String>): R

  public fun visitString(): R

  public fun visitInteger(): R

  public fun visitNumber(): R

  public fun visitArray(items: ProtocolMetaModel.ArrayItemType): R

  public fun visitObject(properties: List<ProtocolMetaModel.ObjectProperty>?): R

  public fun visitMap(): R

  public fun visitUnknown(): R
}