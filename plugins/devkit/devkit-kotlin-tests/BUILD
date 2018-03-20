load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "devkit-kotlin-tests",
    iml_files = ["devkit-kotlin-tests.iml"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # fails after IDEA 181.2784.17 merge
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea/plugins/devkit/jps-plugin:devkit-jps-plugin",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/devkit[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
        "//tools/idea/platform/util[module, test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
    ],
)
