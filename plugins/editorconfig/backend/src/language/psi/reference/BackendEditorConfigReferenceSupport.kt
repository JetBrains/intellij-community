package org.editorconfig.language.psi.reference

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigFlatOptionKey
import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.editorconfig.common.syntax.psi.impl.EditorConfigReferenceSupport
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiReference
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigReferenceDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor
import org.editorconfig.language.util.EditorConfigDescriptorUtil

internal class BackendEditorConfigReferenceSupport() : EditorConfigReferenceSupport {

  override fun getReference(element: EditorConfigHeader): PsiReference {
    return EditorConfigHeaderReference(element)
  }

  override fun getReference(element: EditorConfigDescribableElement): PsiReference? {
    if (element is EditorConfigFlatOptionKey) {
      return EditorConfigFlatOptionKeyReference(element)
    }
    return when (val descriptor = element.getDescriptor(false)) {
      is EditorConfigDeclarationDescriptor -> EditorConfigDeclarationReference(element)
      is EditorConfigReferenceDescriptor -> EditorConfigIdentifierReference(element, descriptor.id)
      is EditorConfigConstantDescriptor -> EditorConfigConstantReference(element)
      is EditorConfigUnionDescriptor -> if (EditorConfigDescriptorUtil.isConstant(descriptor)) {
        EditorConfigConstantReference(element)
      }
      else {
        logger<BackendEditorConfigReferenceSupport>().warn("Got non-constant union")
        null
      }
      else -> null
    }
  }
}
