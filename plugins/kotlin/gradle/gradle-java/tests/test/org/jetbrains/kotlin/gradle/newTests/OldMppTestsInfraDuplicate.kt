// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

/**
 * Marker-annotation to mark classes from the old Multiplatform Importing Tests
 * infrastructure, that are to be removed soon.
 *
 * If you make changes in those classes, please duplicate them in new infra as well
 *
 * Contact @dsavvinov or @pavel.kargashinsky in case of any questions.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class OldMppTestsInfraDuplicate
