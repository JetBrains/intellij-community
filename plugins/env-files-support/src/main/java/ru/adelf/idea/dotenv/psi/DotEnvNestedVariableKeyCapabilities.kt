package ru.adelf.idea.dotenv.psi

import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.psi.PsiNameIdentifierOwner

interface DotEnvNestedVariableKeyCapabilities: PsiNameIdentifierOwner, PsiExternalReferenceHost {
}