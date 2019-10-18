## JPS Portable Cache Loader [193](http://repo.labs.intellij.net/list/intellij-jps-compilation-caches/plugin/193/jps-cache.zip) / [201](https://repo.labs.intellij.net/list/intellij-jps-compilation-caches/plugin/201/jps-cache.zip)
This plugin develops for reducing daily spending time for waiting compilation after pull from repository.
The goal achieves by download already built compilations outputs and caches produced by the JPS build system. This data provides an incremental compilation.

### Important note
**It's preferred to give IDEA some time after first project open (it's needed for filling internal structure).**
We can get read of it in future by implementing first point from list **What should be done**  
**If you have any trouble try to remove folder `jps-cache-loader` from plugins folder and reopen the project(it's the location of this painful structure).**  
**If you think that compilation outputs old enough you can remove them and the plugin will download the fresh one.**
### Enabling
For its work, you should enable separate flag in registry `compiler.build.portable.caches`. After that, each time when you change the commit 
you will get a notification with a proposal to download already existing on the server caches (if they exist).
 - Example of notification which you will get after pull: `Compile server contains caches for the 1th commit behind of yours. Do you want to update your data?`
 - Example of notification on success loading: `Update compilation caches completed successfully in 142 s` on fail: `Update compilation caches failed`



What was done:
- [x] JPS changes for producing portable and deterministic caches via relativisers (covered only Intellij cases)
- [x] CLI tool for comparing JPS caches
- [x] TC job for providing caches and compilation outputs for as many commits as possible
- [x] Plugin for loading and merging all related for JPS data (it's also possible to upload your own caches to server)
 
What should be done(first priority):
- [ ] Create unambiguous matching between module sources and existing compilation outputs
- [ ] Reduce download size (for now JPS caches entirely loads for commit around 300Mb)
- [ ] Make the same mechanic to provide incremental compilation for any TC agent

#### Additional info
Cache server available only in the internal network or by VPN [link](https://repo.labs.intellij.net/list/intellij-jps-compilation-caches/)  
List of commits existing on server [link](https://repo.labs.intellij.net/list/intellij-jps-compilation-caches/caches/)  
TC build which fill cache server [link](https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_Idea_Experiments_CompileInc_JpsCaches#all-projects)