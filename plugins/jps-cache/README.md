## JPS Portable Cache Loader
This plugin develops for reducing daily spending time for waiting compilation after pull from repository.
The goal achieves by download already built compilations outputs and caches produced by the JPS build system. This data provides an incremental compilation.

### Enabling
For its work, you should enable separate flag in registry `compiler.build.portable.caches`. After that, each time when you change the commit 
you will get a notification with a proposal to download already existing on the server caches (if they exist).


What was done:
- [x] JPS changes for producing portable and deterministic caches via relativisers (covered only Intellij cases)
- [x] CLI tool for comparing JPS caches
- [x] TC job for providing caches and compilation outputs for as many commits as possible
- [x] Plugin for loading and merging all related for JPS data (it's also possible to upload your own caches to server)
 
What should be done(first priority):
- [ ] Create unambiguous matching between module sources and existing compilation outputs
- [ ] Reduce download size (for now JPS caches entirely loads for commit around 300Mb)
- [ ] Make the same mechanic to provide incremental compilation for any TC agent 