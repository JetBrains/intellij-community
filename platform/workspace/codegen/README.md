## Code-generator API project

---

The project contains set of interfaces used by Workspace Model's generator.
It describes an API of the generator and language-agnostic metamodel used by them.
The project has to be open as a regular **Gradle** project despite the fact that it's located in IntelliJ sources.

### Distribution
API is connected to other projects as **JAR** library. To promote the new version of API you have to:
- Increase the version of the artifact at [build.gradle.kts](build.gradle.kts)
- Run the corresponding [task](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_IjWsmCodegenPublisher#all-projects) on TC
- Check that the new version of the artifact was uploaded to [intellij-dependencies](https://jetbrains.team/p/ij/packages/maven/intellij-dependencies/com.jetbrains.intellij.platform/workspace-model-codegen)
- Update the version of dependency in [IntelliJ project](https://jetbrains.team/p/ij/repositories/ultimate/files/.idea/libraries/workspace_model_codegen.xml) and in [Code-Generator Impl](https://jetbrains.team/p/ij/repositories/ultimate/files/community/platform/workspace/codegen-impl/build.gradle.kts)  
