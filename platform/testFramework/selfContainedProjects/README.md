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