// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.facet.FacetTypeRegistry
import com.intellij.facet.impl.FacetUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.fixtures.impl.JavaModuleFixtureBuilderImpl
import com.intellij.testFramework.fixtures.impl.ModuleFixtureBuilderImpl
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
interface KotlinModuleFixtureBuilder : JavaModuleFixtureBuilder<KotlinModuleTestFixture> {



    //fun addWebRoot(rootPath: @NonNls String, relativePath: @NonNls String): com.intellij.testFramework.builders.WebModuleFixtureBuilder
    //fun setWebXml(path: @NonNls String): com.intellij.testFramework.builders.WebModuleFixtureBuilder
}

class KotlinModuleFixtureBuilderImpl(val fixtureBuilder: TestFixtureBuilder<IdeaProjectTestFixture>) :
    JavaModuleFixtureBuilderImpl<KotlinModuleTestFixture>(StdModuleTypes.JAVA, fixtureBuilder), KotlinModuleFixtureBuilder {

    override fun instantiateFixture() = KotlinModuleTestFixtureImpl(this)
    override fun createModule(): Module {
        val module = super.createModule()
        FacetUtil.addFacet(module, FacetTypeRegistry.getInstance().findFacetType(KotlinFacetType.ID))
        return module
    }
}


interface KotlinModuleTestFixture : ModuleFixture {
    //fun setWebXml(webXmlPath: @NonNls String?)
    val kotlinFacet: KotlinFacet?
}

class KotlinModuleTestFixtureImpl(builder: ModuleFixtureBuilderImpl<*>?) : ModuleFixtureImpl(builder!!), KotlinModuleTestFixture {
    override val kotlinFacet: KotlinFacet?
        get() = KotlinFacet.get(module)!!

}


