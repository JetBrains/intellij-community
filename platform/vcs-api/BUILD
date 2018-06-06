load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.vcs",
    srcs = ["src"],
    iml_files = ["intellij.platform.vcs.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/platform-api:intellij.platform.ide",
        "//tools/idea/platform/vcs-api/vcs-api-core:intellij.platform.vcs.core",
        "//tools/idea/platform/diff-api:intellij.platform.diff",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/.idea/libraries:microba",
        "//tools/idea/platform/vcs-api/vcs-api-core:intellij.platform.vcs.core[module]",
        "//tools/idea/platform/diff-api:intellij.platform.diff[module]",
        "//tools/idea/.idea/libraries:StreamEx",
        "//tools/idea/.idea/libraries:JDOM",
    ],
)
