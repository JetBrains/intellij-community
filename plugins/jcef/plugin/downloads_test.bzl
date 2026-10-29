"""Goldens of the URL shape of the JCEF archives.

See `@community//build:downloads_test.bzl` for why these goldens exist.
"""

load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load("@community//build:host_platforms.bzl", "HOST_PLATFORMS")
load(":downloads.bzl", "jcef_url")

_JBR = "https://cache-redirector.jetbrains.com/intellij-jbr"

# CommunityRepositoryModules.jcefDownloadUrl, for `jcefBuild=263-b10`
_JCEF_URLS = {
    "darwin_aarch64": _JBR + "/jcef-osx-aarch64-263-b10.tar.gz",
    "darwin_x64": _JBR + "/jcef-osx-x64-263-b10.tar.gz",
    "linux_aarch64": _JBR + "/jcef-linux-aarch64-263-b10.tar.gz",
    "linux_x64": _JBR + "/jcef-linux-x64-263-b10.tar.gz",
    "windows_aarch64": _JBR + "/jcef-windows-aarch64-263-b10.tar.gz",
    "windows_x64": _JBR + "/jcef-windows-x64-263-b10.tar.gz",
}

def _jcef_url_test_impl(ctx):
    env = unittest.begin(ctx)
    asserts.equals(env, sorted(HOST_PLATFORMS), sorted(_JCEF_URLS))
    for platform, expected in _JCEF_URLS.items():
        asserts.equals(env, expected, jcef_url(platform, "263-b10"), platform)
    return unittest.end(env)

jcef_url_test = unittest.make(_jcef_url_test_impl)

def downloads_test_suite(name):
    """The URL goldens of the JCEF downloads."""
    unittest.suite(
        name,
        jcef_url_test,
    )
