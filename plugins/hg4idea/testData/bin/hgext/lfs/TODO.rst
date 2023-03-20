Prior to removing (EXPERIMENTAL)
--------------------------------

These things affect UI and/or behavior, and should probably be implemented (or
ruled out) prior to taking off the experimental shrinkwrap.

#. Finish the `hg convert` story

   * Add an argument to accept a rules file to apply during conversion?
     Currently `lfs.track` is the only way to affect the conversion.
   * drop `lfs.track` config settings
   * splice in `.hglfs` file for normal repo -> lfs conversions?

#. Stop uploading blobs when pushing between local repos

   * Could probably hardlink directly to the other local repo's store
   * Support inferring `lfs.url` for local push/pull (currently only supports
     http)

#. Stop uploading blobs on strip/amend/histedit/etc.

   * This seems to be a side effect of doing it for `hg bundle`, which probably
     makes sense.

#. Handle a server with the extension loaded and a client without the extension
   more gracefully.

   * `changegroup3` is still experimental, and not enabled by default.
   * Figure out how to `introduce LFS to the server repo
     <https://www.mercurial-scm.org/pipermail/mercurial-devel/2018-September/122281.html>`_.
     See the TODO in test-lfs-serve.t.

#. Remove `lfs.retry` hack in client?  This came from FB, but it's not clear why
   it is/was needed.

#. `hg export` currently writes out the LFS blob.  Should it write the pointer
   instead?

   * `hg diff` is similar, and probably shouldn't see the pointer file

#. Show to-be-applied rules with `hg files -r 'wdir()' 'set:lfs()'`

   * `debugignore` can show file + line number, so a dedicated command could be
     useful too.

#. Filesets, revsets and templates

   * A dedicated revset should be faster than `'file(set:lfs())'`
   * Attach `{lfsoid}` and `{lfspointer}` to `general keywords
     <https://www.mercurial-scm.org/pipermail/mercurial-devel/2018-January/110251.html>`_,
     IFF the file is a blob
   * Drop existing items that would be redundant with general support

#. Can `grep` avoid downloading most things?

   * Add a command option to skip LFS blobs?

#. Add a flag that's visible in `hg files -v` to indicate external storage?

#. Server side issues

   * Check for local disk space before allowing upload.  (I've got a patch for
     this.)
   * Make sure the http codes used are appropriate.
   * `Why is copying the Authorization header into the JSON payload necessary
     <https://www.mercurial-scm.org/pipermail/mercurial-devel/2018-April/116230.html>`_?
   * `LFS-Authenticate` header support in client and server(?)

#. Add locks on cache and blob store

   * This is complicated with a global store, and multiple potentially unrelated
     local repositories that reference the same blob.
   * Alternately, maybe just handle collisions when trying to create the same
     blob in the store somehow.

#. Are proper file sizes reported in `debugupgraderepo`?

#. Finish prefetching files

   * `-T {data}`  (other than cat?)
   * `verify`
   * `grep`

#. Output cleanup

   * Can we print the url when connecting to the blobstore?  (A sudden
     connection refused after pulling commits looks confusing.)  Problem is,
     'pushing to main url' is printed, and then lfs wants to upload before going
     back to the main repo transfer, so then *that* could be confusing with
     extra output. (This is kinda improved with 380f5131ee7b and 9f78d10742af.)

   * Add more progress indicators?  Uploading a large repo looks idle for a long
     time while it scans for blobs in each outgoing revision.

   * Print filenames instead of hashes in error messages

     * subrepo aware paths, where necessary

   * Is existing output at the right status/note/debug level?

#. Can `verify` be done without downloading everything?

   * If we know that we are talking to an hg server, we can leverage the fact
     that it validates in the Batch API portion, and skip d/l altogether.  OTOH,
     maybe we should download the files unconditionally for forensics.  The
     alternative is to define a custom transfer handler that definitively
     verifies without transferring, and then cache those results.  When verify
     comes looking, look in the cache instead of actually opening the file and
     processing it.

   * Yuya has concerns about when blob fetch takes place vs when revlog is
     verified.  Since the visible hash matches the blob content, I don't think
     there's a way to verify the pointer file that's actually stored in the
     filelog (other than basic JSON checks).  Full verification requires the
     blob.  See
     https://www.mercurial-scm.org/pipermail/mercurial-devel/2018-April/116133.html

   * Opening a corrupt pointer file aborts.  It probably shouldn't for verify.


Future ideas/features/polishing
-------------------------------

These aren't in any particular order, and are things that don't have obvious BC
concerns.

#. Garbage collection `(issue5790) <https://bz.mercurial-scm.org/show_bug.cgi?id=5790>`_

   * This gets complicated because of the global cache, which may or may not
     consist of hardlinks to the repo, and may be in use by other repos.  (So
     the gc may be pointless.)

#. `Compress blobs <https://github.com/git-lfs/git-lfs/issues/260>`_

   * 700MB repo becomes 2.5GB with all lfs blobs
   * What implications are there for filesystem paths that don't indicate
     compression?  (i.e. how to share with global cache and other local repos?)
   * Probably needs to be stored under `.hg/store/lfs/zstd`, with a repo
     requirement.
   * Allow tuneable compression type and settings?
   * Support compression over the wire if both sides understand the compression?
   * `debugupgraderepo` to convert?
   * Probably not worth supporting compressed and uncompressed concurrently

#. Determine things to upload with `readfast()
   <https://www.mercurial-scm.org/pipermail/mercurial-devel/2018-August/121315.html>`_

   * Significantly faster when pushing an entire large repo to http.
   * Causes test changes to fileset and templates; may need both this and
     current methods of lookup.

#. Is a command to download everything needed?  This would allow copying the
   whole to a portable drive.  Currently this can be effected by running
   `hg verify`.

#. Stop reading in entire file into one buffer when passing through filelog
   interface

   * `Requires major replumbing to core
     <https://www.mercurial-scm.org/wiki/HandlingLargeFiles>`_

#. Keep corrupt files around in 'store/lfs/incoming' for forensics?

   * Files should be downloaded to 'incoming', and moved to normal location when
     done.

#. Client side path enhancements

   * Support paths.default:lfs = ... style paths
   * SSH -> https server inference

     * https://www.mercurial-scm.org/pipermail/mercurial-devel/2018-April/115416.html
     * https://github.com/git-lfs/git-lfs/blob/master/docs/api/server-discovery.md#guessing-the-server

#. Server enhancements

   * Add support for transfer quotas?
   * Download should be able to send the file in chunks, without reading the
     whole thing into memory
     (https://www.mercurial-scm.org/pipermail/mercurial-devel/2018-March/114584.html)
   * Support for resuming transfers

#. Handle 3rd party server storage.

   * Teach client to handle lfs `verify` action.  This is needed after the
     server instructs the client to upload the file to another server, in order
     to tell the server that the upload completed.
   * Teach the server to send redirects if configured, and process `verify`
     requests.

#. `Is any hg-git work needed
   <https://groups.google.com/d/msg/hg-git/XYNQuudteeM/ivt8gXoZAAAJ>`_?
