// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.project;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.compiler.IDELanguageSettingsProviderKt;
import org.jetbrains.kotlin.platform.*;
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms;
import org.jetbrains.kotlin.psi.KtCodeFragment;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactoryKt;
import org.jetbrains.kotlin.scripting.definitions.DefinitionsKt;
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition;

public class TargetPlatformDetector {
    public static final TargetPlatformDetector INSTANCE = new TargetPlatformDetector();
    private static final Logger LOG = Logger.getInstance(TargetPlatformDetector.class);

    private TargetPlatformDetector() {
    }

    @NotNull
    public static TargetPlatform getPlatform(@NotNull KtFile file) {
        TargetPlatform explicitPlatform = PlatformKt.getForcedTargetPlatform(file);
        if (explicitPlatform != null) return explicitPlatform;

        if (file instanceof KtCodeFragment) {
            KtFile contextFile = ((KtCodeFragment) file).getContextContainingFile();
            if (contextFile != null) {
                return getPlatform(contextFile);
            }
        }

        PsiElement context = KtPsiFactoryKt.getAnalysisContext(file);
        if (context != null) {
            PsiFile contextFile = context.getContainingFile();
            // TODO(dsavvinov): Get default platform with proper target
            return contextFile instanceof KtFile ? getPlatform((KtFile) contextFile) : JvmPlatforms.INSTANCE.getUnspecifiedJvmPlatform();
        }

        final ScriptDefinition scriptDefinition =
                ReadAction.compute(() -> file.isScript() ? DefinitionsKt.findScriptDefinition(file) : null);
        if (scriptDefinition != null) {
            return getPlatform4Script(file.getProject(), file.getOriginalFile().getVirtualFile(), scriptDefinition);
        }

        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        if (virtualFile != null) {
            Module moduleForFile = ProjectFileIndex.SERVICE.getInstance(file.getProject()).getModuleForFile(virtualFile);
            if (moduleForFile != null) {
                return getPlatform(moduleForFile);
            }
        }

        return DefaultIdeTargetPlatformKindProvider.Companion.getDefaultPlatform();
    }

    @NotNull
    public static TargetPlatform getPlatform(@NotNull Module module) {
        return ProjectStructureUtil.getCachedPlatformForModule(module);
    }

    @NotNull
    public static TargetPlatform getPlatform4Script(
            @NotNull Project project,
            @NotNull VirtualFile file,
            @NotNull ScriptDefinition scriptDefinition
    ) {
        TargetPlatformVersion targetPlatformVersion =
                IDELanguageSettingsProviderKt.getTargetPlatformVersionForScript(project, file, scriptDefinition);
        return getPlatform4ScriptImpl(targetPlatformVersion, scriptDefinition);
    }

    @NotNull
    private static TargetPlatform getPlatform4ScriptImpl(
            TargetPlatformVersion targetPlatformVersion,
            @NotNull ScriptDefinition scriptDefinition
    ) {
        if (!targetPlatformVersion.equals(TargetPlatformVersion.NoVersion.INSTANCE)) {
            for (TargetPlatform compilerPlatform : CommonPlatforms.INSTANCE.getAllSimplePlatforms()) {
                SimplePlatform simplePlatform = CollectionsKt.single(compilerPlatform);
                if (simplePlatform.getTargetPlatformVersion() == targetPlatformVersion) {
                    return compilerPlatform;
                }
            }
        }

        String platformNameFromScriptDefinition = scriptDefinition.getPlatform();
        for (TargetPlatform compilerPlatform : CommonPlatforms.INSTANCE.getAllSimplePlatforms()) {
            // FIXME(dsavvinov): get rid of matching by name
            SimplePlatform simplePlatform = CollectionsKt.single(compilerPlatform);
            if (simplePlatform.getPlatformName().equals(platformNameFromScriptDefinition)) {
                return compilerPlatform;
            }
        }

        return DefaultIdeTargetPlatformKindProvider.Companion.getDefaultPlatform();
    }
}
