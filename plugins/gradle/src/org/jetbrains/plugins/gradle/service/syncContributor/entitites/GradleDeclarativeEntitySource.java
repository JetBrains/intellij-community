// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.entitites;

import com.intellij.platform.workspace.storage.url.VirtualFileUrl;

public record GradleDeclarativeEntitySource(VirtualFileUrl projectRootUrl) implements GradleEntitySource {

}
