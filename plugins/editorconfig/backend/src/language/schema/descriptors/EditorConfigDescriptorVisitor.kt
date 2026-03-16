// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors

import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigListDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigNumberDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigPairDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigStringDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnsetValueDescriptor

interface EditorConfigDescriptorVisitor {
  fun visitDescriptor(descriptor: EditorConfigDescriptor) {}

  fun visitConstant(constant: EditorConfigConstantDescriptor): Unit = visitDescriptor(constant)
  fun visitOption(option: EditorConfigOptionDescriptor): Unit = visitDescriptor(option)
  fun visitNumber(number: EditorConfigNumberDescriptor): Unit = visitDescriptor(number)
  fun visitUnion(union: EditorConfigUnionDescriptor): Unit = visitDescriptor(union)
  fun visitList(list: EditorConfigListDescriptor): Unit = visitDescriptor(list)
  fun visitPair(pair: EditorConfigPairDescriptor): Unit = visitDescriptor(pair)
  fun visitQualifiedKey(qualifiedKey: EditorConfigQualifiedKeyDescriptor): Unit = visitDescriptor(qualifiedKey)
  fun visitString(string: EditorConfigStringDescriptor): Unit = visitDescriptor(string)
  fun visitReference(reference: EditorConfigReferenceDescriptor): Unit = visitDescriptor(reference)
  fun visitDeclaration(declaration: EditorConfigDeclarationDescriptor): Unit = visitDescriptor(declaration)
  fun visitUnset(unset: EditorConfigUnsetValueDescriptor): Unit = visitDescriptor(unset)
}
