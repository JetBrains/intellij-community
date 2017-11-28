load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "junit5_rt_tests",
    iml_files = ["junit5_rt_tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:credential-store",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/junit5_rt[module]",
        "//tools/idea:jps-builders_and_others[module, test]",
        "//tools/idea:execution-impl[module, test]",
        "//tools/idea/plugins/junit[module, test]",
        "//tools/idea:java-tests[module, test]",
        "//tools/idea:aether-dependency-resolver[module, test]",
        "//tools/idea/java/compiler/openapi:compiler-openapi[module, test]",
        "//tools/idea/.idea/libraries:JUnit5[test]",
        "//tools/idea/.idea/libraries:junit5_rt[test]",
        "//prebuilts/tools/common/m2/repository/org/apiguardian/apiguardian-api/1.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/jupiter/junit-jupiter-api/5.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/jupiter/junit-jupiter-engine/5.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/platform/junit-platform-commons/1.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/platform/junit-platform-engine/1.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/opentest4j/opentest4j/1.0.0:jar[test]",
        "//prebuilts/tools/common/m2/repository/org/junit/vintage/junit-vintage-engine/4.12.0:jar[test]",
    ],
)
