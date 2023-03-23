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
- Increase the version of the generator the [Workspace Model](https://jetbrains.team/p/ij/repositories/intellij/files/786c6a41a3c6209c3b385c579ea5cbee5051a198/community/platform/workspaceModel/storage/src/com/intellij/workspaceModel/storage/generatedCodeCompatibility.kt?tab=source&line=10&lines-count=1)

