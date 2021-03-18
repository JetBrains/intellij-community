// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

class GradleBuildScriptBuilderEx : GradleBuildScriptBuilder()


@JvmOverloads
fun GradleBuildScriptBuilder.withBuildScriptMavenCentral(useOldStyleMetadata: Boolean = false) = withBuildScriptMavenCentral()

@JvmOverloads
fun GradleBuildScriptBuilder.withMavenCentral(useOldStyleMetadata: Boolean = false) = withMavenCentral()
