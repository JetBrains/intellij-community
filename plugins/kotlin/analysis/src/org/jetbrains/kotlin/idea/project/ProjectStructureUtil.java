// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms;
import org.jetbrains.kotlin.platform.TargetPlatform;

public class ProjectStructureUtil {
    private static final Key<CachedValue<TargetPlatform>> PLATFORM_FOR_MODULE = Key.create("PLATFORM_FOR_MODULE");

    private ProjectStructureUtil() {
    }

    @NotNull
    /* package */ static TargetPlatform getCachedPlatformForModule(@NotNull final Module module) {
        CachedValue<TargetPlatform> result = module.getUserData(PLATFORM_FOR_MODULE);
        if (result == null) {
            result = CachedValuesManager.getManager(module.getProject()).createCachedValue(() -> {
                TargetPlatform platform = PlatformKt.getPlatform(module);
                if (platform == null) {
                    platform = JvmPlatforms.INSTANCE.getDefaultJvmPlatform();
                }
                return CachedValueProvider.Result.create(platform,
                                                         ProjectRootModificationTracker.getInstance(module.getProject()));
            }, false);

            module.putUserData(PLATFORM_FOR_MODULE, result);
        }

        return result.getValue();
    }
}
