/*
package ru.adelf.idea.dotenv.js;

import com.intellij.lang.javascript.JavaScriptFileType;
import com.intellij.lang.javascript.TypeScriptFileType;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.Collections;

public class JsEnvironmentVariablesUsagesProvider implements EnvironmentVariablesUsagesProvider {
    @Override
    public boolean acceptFile(VirtualFile file) {
        return (file.getFileType().equals(JavaScriptFileType.INSTANCE) || file.getFileType().equals(TypeScriptFileType.INSTANCE))
                && !file.getPath().contains("/node_modules/");
    }

    @NotNull
    @Override
    public Collection<KeyUsagePsiElement> getUsages(PsiFile psiFile) {
        if(psiFile instanceof JSFile) {
            JsEnvironmentCallsVisitor visitor = new JsEnvironmentCallsVisitor();
            psiFile.acceptChildren(visitor);

            return visitor.getCollectedItems();
        }

        return Collections.emptyList();
    }
}
*/
