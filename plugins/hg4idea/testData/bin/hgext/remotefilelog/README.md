remotefilelog
=============

The remotefilelog extension allows Mercurial to clone shallow copies of a repository such that all file contents are left on the server and only downloaded on demand by the client. This greatly speeds up clone and pull performance for repositories that have long histories or that are growing quickly.

In addition, the extension allows using a caching layer (such as memcache) to serve the file contents, thus providing better scalability and reducing server load.

Installing
==========

**NOTE:** See the limitations section below to check if remotefilelog will work for your use case.

remotefilelog can be installed like any other Mercurial extension. Download the source code and add the remotefilelog subdirectory to your `hgrc`:

    :::ini
    [extensions]
    remotefilelog=path/to/remotefilelog/remotefilelog

Configuring
-----------

**Server**

* `server` (required) - Set to 'True' to indicate that the server can serve shallow clones.
* `serverexpiration` - The server keeps a local cache of recently requested file revision blobs in .hg/remotefilelogcache. This setting specifies how many days they should be kept locally.  Defaults to 30.

An example server configuration:

    :::ini
    [remotefilelog]
    server = True
    serverexpiration = 14

**Client**

* `cachepath` (required) - the location to store locally cached file revisions
* `cachelimit` - the maximum size of the cachepath. By default it's 1000 GB.
* `cachegroup` - the default unix group for the cachepath. Useful on shared systems so multiple users can read and write to the same cache.
* `cacheprocess` - the external process that will handle the remote caching layer. If not set, all requests will go to the Mercurial server.
* `fallbackpath` - the Mercurial repo path to fetch file revisions from. By default it uses the paths.default repo. This setting is useful for cloning from shallow clones and still talking to the central server for file revisions.
* `includepattern` - a list of regex patterns matching files that should be kept remotely. Defaults to all files.
* `excludepattern` - a list of regex patterns matching files that should not be kept remotely and should always be downloaded.
* `pullprefetch` - a revset of commits whose file content should be prefetched after every pull. The most common value for this will be '(bookmark() + head()) & public()'. This is useful in environments where offline work is common, since it will enable offline updating to, rebasing to, and committing on every head and bookmark.

An example client configuration:

    :::ini
    [remotefilelog]
    cachepath = /dev/shm/hgcache
    cachelimit = 2 GB

Using as a largefiles replacement
---------------------------------

remotefilelog can theoretically be used as a replacement for the largefiles extension. You can use the `includepattern` setting to specify which directories or file types are considered large and they will be left on the server. Unlike the largefiles extension, this can be done without converting the server repository. Only the client configuration needs to specify the patterns.

The include/exclude settings haven't been extensively tested, so this feature is still considered experimental.

An example largefiles style client configuration:

    :::ini
    [remotefilelog]
    cachepath = /dev/shm/hgcache
    cachelimit = 2 GB
    includepattern = *.sql3
      bin/*

Usage
=====

Once you have configured the server, you can get a shallow clone by doing:

    :::bash
    hg clone --shallow ssh://server//path/repo

After that, all normal mercurial commands should work.

Occasionly the client or server caches may grow too big. Run `hg gc` to clean up the cache. It will remove cached files that appear to no longer be necessary, or any files that exceed the configured maximum size. This does not improve performance; it just frees up space.

Limitations
===========

1. The extension must be used with Mercurial 3.3 (commit d7d08337b3f6) or higher (earlier versions of the extension work with earlier versions of Mercurial though, up to Mercurial 2.7).

2. remotefilelog has only been tested on linux with case-sensitive filesystems. It should work on other unix systems but may have problems on case-insensitive filesystems.

3. remotefilelog only works with ssh based Mercurial repos. http based repos are currently not supported, though it shouldn't be too difficult for some motivated individual to implement.

4. Tags are not supported in completely shallow repos. If you use tags in your repo you will have to specify `excludepattern=.hgtags` in your client configuration to ensure that file is downloaded. The include/excludepattern settings are experimental at the moment and have yet to be deployed in a production environment.

5. A few commands will be slower. `hg log <filename>` will be much slower since it has to walk the entire commit history instead of just the filelog. Use `hg log -f <filename>` instead, which remains very fast.

Contributing
============

Patches are welcome as pull requests, though they will be collapsed and rebased to maintain a linear history.  Tests can be run via:

    :::bash
    cd tests
    ./run-tests --with-hg=path/to/hgrepo/hg

We (Facebook) have to ask for a "Contributor License Agreement" from someone who sends in a patch or code that we want to include in the codebase. This is a legal requirement; a similar situation applies to Apache and other ASF projects.

If we ask you to fill out a CLA we'll direct you to our [online CLA page](https://developers.facebook.com/opensource/cla) where you can complete it easily. We use the same form as the Apache CLA so that friction is minimal.

License
=======

remotefilelog is made available under the terms of the GNU General Public License version 2, or any later version. See the COPYING file that accompanies this distribution for the full text of the license.
