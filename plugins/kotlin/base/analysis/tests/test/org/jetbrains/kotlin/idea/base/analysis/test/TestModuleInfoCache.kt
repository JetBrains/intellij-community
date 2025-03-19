// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.test

class TestModuleInfoCache : AbstractModuleInfoCacheTest() {

    fun testSimple() {
        assertHasModules("SDK")

        val fooModule = createModule("foo") {
            addSourceFolder("src", isTest = false)
            addSourceFolder("test", isTest = true)
        }

        assertHasModules("SDK", "foo:src", "foo:test")

        val barModule = createModule("bar") {
            // No source folders
        }

        assertHasModules("SDK", "foo:src", "foo:test")

        updateModule(fooModule) {
            removeSourceFolder("src")
        }

        assertHasModules("SDK", "foo:test")

        updateModule(barModule) {
            addSourceFolder("test", isTest = true)
        }

        assertHasModules("SDK", "foo:test", "bar:test")

        val bazModule = createModule("baz") {
            addSourceFolder("src", isTest = false)
            addSourceFolder("test", isTest = true)
            addSourceFolder("performanceTest", isTest = true)
        }

        assertHasModules("SDK", "foo:test", "bar:test", "baz:src", "baz:test")

        renameModule(bazModule, "boo")

        assertHasModules("SDK", "foo:test", "bar:test", "boo:src", "boo:test")

        renameModule(barModule, "baq")

        assertHasModules("SDK", "foo:test", "baq:test", "boo:src", "boo:test")

        removeModule(barModule)

        assertHasModules("SDK", "foo:test", "boo:src", "boo:test")
    }
}
