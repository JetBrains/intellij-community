package ru.adelf.idea.dotenv.extension.symbols;

import com.intellij.dotenv.icons.DotenvIcons;
import com.intellij.model.Pointer;
import com.intellij.navigation.NavigatableSymbol;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.navigation.NavigationRequest;
import com.intellij.platform.backend.navigation.NavigationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
class DotEnvKeyUsageSymbol implements Pointer<DotEnvKeyUsageSymbol>, NavigatableSymbol, NavigationTarget {
    private final PsiElement psiElement;

    DotEnvKeyUsageSymbol(PsiElement psiElement) {
        this.psiElement = psiElement;
    }

    @Override
    public @Nullable DotEnvKeyUsageSymbol dereference() {
        return this;
    }

    @Override
    public @NotNull Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project) {
        return List.of();
    }

    @Override
    public @NotNull Pointer<DotEnvKeyUsageSymbol> createPointer() {
        return this;
    }

    @Override
    public @NotNull TargetPresentation computePresentation() {
        PsiFile psiFile = psiElement.getContainingFile();

        if (psiFile == null) {
            return TargetPresentation.builder(psiElement.getText())
                    .presentation();
        }

        int lineNumber = getLineNumber(psiElement, psiFile);

        String filePath = getFilePath(psiFile);

        if (filePath == null) {
            filePath = psiFile.getName();
        }

        String locationString;
        if (lineNumber == -1) {
            locationString = filePath;
        } else {
            locationString = filePath + ":" + lineNumber;
        }

        return TargetPresentation.builder(psiElement.getText())
                .locationText(locationString, psiFile.getIcon(Iconable.ICON_FLAG_VISIBILITY))
                .icon(DotenvIcons.Env)
                .presentation();
    }

    @Override
    public @Nullable NavigationRequest navigationRequest() {
        return NavigationRequest.sourceNavigationRequest(psiElement.getContainingFile(), psiElement.getTextRange());
    }

    private int getLineNumber(@NotNull PsiElement psiElement, @NotNull PsiFile psiFile) {
        Document document = psiFile.getViewProvider().getDocument();

        if (document == null) return -1;

        return document.getLineNumber(psiElement.getTextOffset()) + 1;
    }

    private @Nullable String getFilePath(@NotNull PsiFile psiFile) {
        String basePath = psiFile.getProject().getBasePath();

        if (basePath == null) return null;

        VirtualFile virtualFile = psiFile.getVirtualFile();

        if (virtualFile == null) return null;

        String filePath = virtualFile.getPath();

        if (filePath.startsWith(basePath)) {
            return filePath.substring(basePath.length());
        } else {
            return null;
        }
    }
}
