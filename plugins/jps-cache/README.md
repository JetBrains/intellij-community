## JPS Portable Cache Loader [\[It can be downloaded from the latest installer\]](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_Idea_Installers)
This plugin develops for reducing daily spending time for waiting compilation after pull from repository.
The goal achieves by download already built compilations outputs and caches produced by the JPS build system. This data provides an incremental compilation.

### Important note
**If you think that compilation outputs old enough you can remove them and the plugin will download the fresh one.**
### Enabling
For its work, you should enable plugin. After that, each time when you change the commit 
you will get a notification with a proposal to download already existing on the server caches (if they exist).
 - Example of notification which you will get after pull: `Compile server contains caches for the 1th commit behind of yours. Do you want to update your data?`
 - Example of notification on success loading: `Update compilation caches completed successfully in 142 s` on fail: `Update compilation caches failed`



What was done:
- [x] JPS changes for producing portable and deterministic caches via relativisers (covered only Intellij cases)
- [x] CLI tool for comparing JPS caches
- [x] TC job for providing caches and compilation outputs for as many commits as possible
- [x] Plugin for loading and merging all related for JPS data (it's also possible to upload your own caches to server)
- [x] Create unambiguous matching between module sources and existing compilation outputs (report produced by JPS work)
 
What should be done(first priority):
- [ ] Reduce download size (for now JPS caches entirely loads for commit around 300Mb)
- [x] Make the same mechanic to provide incremental compilation for any TC agent

#### Additional info
Globally available cache server protected by Space authorization [link](https://cache-redirector.jetbrains.com/www.jetbrains.com/jps-cache/intellij)  
List of commits existing on server [link](https://cache-redirector.jetbrains.com/www.jetbrains.com/jps-cache/intellij/commit_history.json)  
CI project [link](https://buildserver.labs.intellij.net/project/ijplatform_master_Idea_Experiments_Jps_Caches_Project?mode=builds)