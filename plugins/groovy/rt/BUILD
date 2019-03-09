load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.groovy.rt",
    srcs = ["src"],
    iml_files = ["intellij.groovy.rt.iml"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Groovy"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/platform/util-class-loader:intellij.platform.util.classLoader[module]",
        "//tools/idea/plugins/groovy/rt-constants:intellij.groovy.constants.rt[module]",
        "//tools/idea/.idea/libraries:Slf4j",
    ],
)
