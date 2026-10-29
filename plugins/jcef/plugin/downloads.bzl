"""The JCEF archive of each platform, which the JCEF plugin layout bundles. `jcefBuild` pins the version."""

load("@community//build:download_helpers.bzl", "INTELLIJ_JBR_URL", "JBR_OS_SUFFIX", "pinned", "read_properties")
load("@community//build:download_rules.bzl", "download_repo")
load("@community//build:host_platforms.bzl", "HOST_PLATFORMS", "platform_parts")

_DEPENDENCIES = Label("@community//build:dependencies/dependencies.properties")

# CommunityRepositoryModules.jcefDownloadUrl
def jcef_url(platform, jcef_build):
    parts = platform_parts(platform)
    return "%s/jcef-%s-%s-%s.tar.gz" % (INTELLIJ_JBR_URL, JBR_OS_SUFFIX[parts.os], parts.arch, jcef_build)

def _jcef_downloads_impl(module_ctx):
    jcef_build = pinned(read_properties(module_ctx, _DEPENDENCIES), _DEPENDENCIES, "jcefBuild")
    for platform in HOST_PLATFORMS:
        download_repo(
            name = "jcef_%s" % platform,
            urls = [jcef_url(platform, jcef_build)],
        )
    return module_ctx.extension_metadata(reproducible = True)

jcef_downloads = module_extension(implementation = _jcef_downloads_impl)
