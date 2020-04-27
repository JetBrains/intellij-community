load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.structureView.impl",
    srcs = ["src"],
    iml_files = ["intellij.platform.structureView.impl.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor",
        "//tools/idea/platform/util:intellij.platform.util",
        "//tools/idea/platform/extensions:intellij.platform.extensions",
        "//tools/idea/platform/core-api:intellij.platform.core",
        "//tools/idea/platform/platform-resources-en:intellij.platform.resources.en",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/extensions:intellij.platform.extensions[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/bootstrap:intellij.platform.bootstrap[module]",
        "//tools/idea/platform/platform-resources-en:intellij.platform.resources.en[module]",
        "//tools/idea/platform/icons:intellij.platform.icons[module]",
        "//tools/idea/.idea/libraries:automaton",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/util:intellij.platform.util.ui[module]",
    ],
)
