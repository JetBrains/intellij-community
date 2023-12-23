## Code-generator Implementation Project

---

It contains the source code of the code generator for the `Workspace Model` entities.
The project has to be open as a regular Gradle project despite the fact that it's located in IntelliJ sources.

Code-generator is distributed as **JAR** archive and downloaded by **DevKit Plugin** to run the generator if the corresponding action was called.


### Why it's a separate project?
This approach is used to simplify the delivery of new changes from the generator to the plugin developers.
Previously it was inconvenient because every new change forced you to install a fresh IntelliJ installer to have an actual generator.
Now it's not needed the actual version will be automatically downloaded by the **DevKit Plugin**.

### Distribution
To deliver changes first of all you need to make them, write corresponding tests, if applicable, and promote the new version of **JAR** archive.
The promotion process:
- Before the promotion increase the version of the artifact at [build.gradle.kts](build.gradle.kts)
- Run the corresponding [task](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_IjWsmCodegenImplPublisher#all-projects) on TC
- Check that the new version of the artifact was uploaded to [intellij-dependencies](https://jetbrains.team/p/ij/packages/maven/intellij-dependencies/com.jetbrains.intellij.platform/workspace-model-codegen-impl)
- Increase the version of the generator the [Workspace Model](https://jetbrains.team/p/ij/repositories/ultimate/files/786c6a41a3c6209c3b385c579ea5cbee5051a198/community/platform/workspaceModel/storage/src/com/intellij/workspaceModel/storage/generatedCodeCompatibility.kt?tab=source&line=10&lines-count=1)


### Known issues and different approaches
**As shared library** - current solution

Issues:
- It's inconvenient to update the generator when we update the storage itself.
  We have to perform the nightly release of the storage and then update the generator itself.
- The source code of the generator is separated from the source code of the monorepo.
- Plugin writers may get some issues related to downloading of the jar library.

**As embedded in SDK/source code**

As the version of the generator is defined by the SDK, there is a hypothesis that we can
include the generator in the SDK build.

Issues:
- For the moment, the implementation of this approach is not clear.
- The IDE developers don't have SDK during the development.
  They have, however, the source code of the generator right on the machine.
  In theory, we can generate the implementations of the entities based using this source code.
  - This approach will require the project to be in "working state" with a compilation not broken
  - In this case, we'll need to compile the sources every time we need to generate the implementation.
    This seems to be more resource consuming than downloading the dependency once.
- It's not completely clear how DevKit should implement the approach of "use class from SDK if this is a plugin
  or use source code if this is an IDE project".