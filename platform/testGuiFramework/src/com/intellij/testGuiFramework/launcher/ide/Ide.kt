package com.intellij.testGuiFramework.launcher.ide

/**
 * @author Sergey Karashevich
 */

data class Ide(val ideType: IdeType, val version: Int, val build: Int) {
    override fun toString(): String {
        return "Ide(ideType=$ideType, version=$version, build=$build)"
    }
}