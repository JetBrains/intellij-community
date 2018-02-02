load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "gradle-tooling-extension-impl",
    srcs = ["src"],
    iml_files = ["gradle-tooling-extension-impl.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/annotations[module]",
        "//tools/idea/platform/external-system-rt[module]",
        "//tools/idea/plugins/gradle/tooling-extension-api:gradle-tooling-extension-api[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools/idea/.idea/libraries:Groovy",
        "//tools/idea/.idea/libraries:gson",
    ],
)

iml_module(
    name = "gradle-tooling-extension-tests",
    iml_files = ["gradle-tooling-extension-tests.iml"],
    tags = ["managed"],
    test_resources = ["testData"],
    test_srcs = ["testSources"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle/tooling-extension-impl:gradle-tooling-extension-impl[module]",
        "//tools/idea/plugins/gradle[module]",
        "//tools/idea:jps-builders_and_others[module, test]",
    ],
)
