// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.descriptors

import org.editorconfig.language.schema.descriptors.impl.*

interface EditorConfigDescriptorVisitor {
  fun visitDescriptor(descriptor: EditorConfigDescriptor) {}

  fun visitConstant(constant: EditorConfigConstantDescriptor) = visitDescriptor(constant)
  fun visitOption(option: EditorConfigOptionDescriptor) = visitDescriptor(option)
  fun visitNumber(number: EditorConfigNumberDescriptor) = visitDescriptor(number)
  fun visitUnion(union: EditorConfigUnionDescriptor) = visitDescriptor(union)
  fun visitList(list: EditorConfigListDescriptor) = visitDescriptor(list)
  fun visitPair(pair: EditorConfigPairDescriptor) = visitDescriptor(pair)
  fun visitQualifiedKey(qualifiedKey: EditorConfigQualifiedKeyDescriptor) = visitDescriptor(qualifiedKey)
  fun visitString(string: EditorConfigStringDescriptor) = visitDescriptor(string)
  fun visitReference(reference: EditorConfigReferenceDescriptor) = visitDescriptor(reference)
  fun visitDeclaration(declaration: EditorConfigDeclarationDescriptor) = visitDescriptor(declaration)
  fun visitUnset(unset: EditorConfigUnsetValueDescriptor) = visitDescriptor(unset)
}
