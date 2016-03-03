package org.jetbrains.protocolModelGenerator

import org.jetbrains.jsonProtocol.ProtocolMetaModel

interface TypeVisitor<R> {
  fun visitRef(refName: String): R

  fun visitBoolean(): R

  fun visitEnum(enumConstants: List<String>): R

  fun visitString(): R

  fun visitInteger(): R

  fun visitNumber(): R

  fun visitArray(items: ProtocolMetaModel.ArrayItemType): R

  fun visitObject(properties: List<ProtocolMetaModel.ObjectProperty>?): R

  fun visitMap(): R

  fun visitUnknown(): R
}