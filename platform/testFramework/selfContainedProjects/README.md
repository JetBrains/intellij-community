# Self-Contained Projects: Caching HTTP Proxy for Offline Gradle Builds

A caching HTTP proxy that enables truly offline Gradle builds by pre-warming an artifact cache.

## Components

- **`CachingHttpProxy.kt`** - HTTP proxy server that caches Maven repository requests
- **`init.gradle.kts`** - Gradle init script that routes all repository URLs through the proxy

## Quick Start

### 1. Start the Proxy

```kotlin
val proxy = CachingHttpProxy(
    listen = InetSocketAddress("127.0.0.1", 8080),
    cacheDir = Path.of("/path/to/cache"),
    offline = false  // Start in online mode
)

println("Proxy URL: ${proxy.proxyUrl}")
// Output: http://127.0.0.1:8080/proxy
```

### 2. Set Environment Variables

```bash
export SELF_CONTAINED_PROXY_URL="http://127.0.0.1:8080/proxy"
export SELF_CONTAINED_VERBOSE="true"  # Optional
```

**Note:** URL must NOT end with a trailing slash.

### 3. Warm the Cache

```bash
gradle --init-script /path/to/init.gradle.kts build
```

This downloads and caches all artifacts through the proxy.

### 4. Switch to Offline Mode

```kotlin
proxy.offline = true
```

Now Gradle builds work completely offline using only the cache.

## How It Works

**Cache Warming (Online Mode):**
1. Proxy intercepts Maven repository requests
2. Fetches artifacts from upstream and caches them locally
3. Caches both content and HTTP headers

**Offline Mode:**
1. All requests served from local cache
2. Cache miss = error (503 Service Unavailable)

## Cache Structure

```
cache/
├── repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.0/
│   ├── kotlin-stdlib-1.9.0.jar
│   ├── kotlin-stdlib-1.9.0.jar.headers
│   ├── kotlin-stdlib-1.9.0.pom
│   └── kotlin-stdlib-1.9.0.pom.headers
└── plugins.gradle.org/m2/...
```

Each artifact has:
- Content file (`.jar`, `.pom`, etc.)
- Headers file (`.headers`) with HTTP status and headers

## Common Use Cases

### CI/CD Pipeline

```bash
# Setup (once)
./gradlew --init-script init.gradle.kts build
tar -czf gradle-cache.tar.gz /path/to/cache

# Each build
tar -xzf gradle-cache.tar.gz
# Start proxy in offline mode
./gradlew --init-script init.gradle.kts build
```

## Troubleshooting

### "SELF_CONTAINED_PROXY_URL must be set"
```bash
export SELF_CONTAINED_PROXY_URL="http://127.0.0.1:8080/proxy"
```

### "Proxy URL must not end with /"
Remove trailing slash from the URL.

### "Not found in cache" (503 Error)
Switch to online mode and re-run build to cache the missing artifact.

### Build Still Accessing Network
Verify init script is applied:
```bash
gradle --init-script /path/to/init.gradle.kts build --info | grep SELF-CONTAINED
```

### Port Already in Use
Use port 0 for automatic port selection:
```kotlin
val proxy = CachingHttpProxy(
    listen = InetSocketAddress("127.0.0.1", 0),
    // ...
)
println("Port: ${proxy.port}")
```

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SELF_CONTAINED_PROXY_URL` | Yes | Proxy base URL (no trailing slash) |
| `SELF_CONTAINED_VERBOSE` | No | Enable verbose logging (default: true) |
# Self-Contained Bazel Projects: Offline Bazel Fixtures

The `bazel` package holds the framework for a Bazel workspace a test imports with no network. The LS `inSaneBazel`
fixture (`language-server/integration-tests/kotlin-standalone/testData/bazel/inSaneBazel`) is the first consumer.

## Parts

- **`HermeticBazelFixture`**: a checked-in workspace. Its package files are checked in as `BUILD.txt`, so the IntelliJ
  repository does not load them. `materialize` copies the sources and renames them back to `BUILD`.
- **`HermeticBazelCache`**: the unpacked offline cache, shared and read-only. Layout:
  `bazelisk-home/downloads/bazelbuild/bazel/<version>/<os>_<arch>/bazel-<version>-<os>-<arch>`, `repo-cache/`, `bcr/`.
- **`HermeticBazelWorkspace.configure`**: writes the offline `.bazelrc` (`--registry=file://<bcr>`,
  `--repository_cache=<repo-cache>`, the remote JDK flags, `--output_base` under a writable root). `close` runs
  `bazel shutdown`, so the caller can delete the output root.
- **`HermeticBazelCacheBuilder`**: regenerates the cache with the network on. Downloads Bazel for every platform,
  runs the caller's warm-up, prefetches the per-platform toolchain repositories with `bazel fetch --repo=`, mirrors
  the registry files the lockfile names, and stages `self-contained/` for the upload.

## Cache key

The archive is named `<prefix>-cache-<key>.zip`, where `key` is the first 12 hex chars of
`sha256(MODULE.bazel.lock + "\n" + .bazelversion)`. The mirror inside the archive is the lockfile's registry file set,
so a lockfile change is the event that needs a new archive. The fixture lockfile changes only with a `MODULE.bazel`
edit or a Bazel bump. The builder fails when Bazel rewrote the lockfile during the warm-up and stages the rewritten
copy next to the cache; commit it into the fixture and run the regeneration again.

## How the offline run stays offline

- The lockfile records registry hashes by the original `https://bcr.bazel.build/` URL. With the registry swapped to
  `file://`, Bazel re-reads every `MODULE.bazel` and `source.json` from the mirror and fetches every archive from the
  repository cache by sha256. `--lockfile_mode=error` is no guard here: the `file://` URLs are not in the lockfile.
- The cache holds only the remote JDK, so the `.bazelrc` forces `--java_runtime_version=remotejdk_21`. `local_jdk`
  does not help: the aspect build still analyses the registered remote JDK toolchains and fetches them.
- The consumer copies the bundled binary to where the code under test looks for Bazel (`installBazelBinary`). The
  IntelliJ Bazel plugin reads `<system>/bazel-plugin/bazelisk`, but only when `forceBazeliskDownload` is on;
  otherwise a `bazel` on `PATH` wins. The LS sets the flag; the plugin's own tests do not.

## Limits

- POSIX only. The heavy tests are disabled on Windows.
- The cache layout is not the layout of a real bazelisk home, so a `.bazeliskrc` with `BAZELISK_HOME=` does not find
  the binary. A consumer must install the binary explicitly.
- The archive label and its `BazelTestDependencyHttpFileDownloader` stay with each consumer; each module owns its
  `*_dependencies.bzl`.
