package com.intellij.buildsystem.model.unified

import com.intellij.buildsystem.model.BuildDependency

data class UnifiedCoordinates(
    val groupId: String?,
    val artifactId: String?,
    val version: String?
) : BuildDependency.Coordinates {

    override val displayName: String
        get() = buildString {
            if (groupId != null) {
                append("$groupId")
            }
            if (artifactId != null) {
                append(":$artifactId")
            }
            if (version != null) {
                append(":$version")
            }
        }
}
