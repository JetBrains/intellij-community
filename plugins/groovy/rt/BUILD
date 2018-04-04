load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.groovy.rt",
    srcs = ["src"],
    iml_files = ["intellij.groovy.rt.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:Groovy"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/plugins/groovy/rt-constants:intellij.groovy.constants.rt[module]",
        "//tools/idea/plugins/groovy/testdata/griffon:griffon-cli-1.1.0",
        "//tools/idea/plugins/groovy/testdata/griffon:griffon-rt-1.1.0",
        "//tools/idea/.idea/libraries:Slf4j",
    ],
)
