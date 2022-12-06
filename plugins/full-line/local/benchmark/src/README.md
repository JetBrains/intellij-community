# Benchmarks for `local`

This module contains benchmarks for local model inference

### How to run

You can implement a simple main method to run benchmark but the most convenient way to run them is to use
a [plugin](https://plugins.jetbrains.com/plugin/7529-jmh-java-microbenchmark-harness).

### Troubleshooting
If the run crashes with an error
```Unable to find the resource: /META-INF/BenchmarkList```
And IDEA does not offer you to enable Annotation Processing you can do it manually to fix the error.
- Go to `.idea/compiler.xml`
- Find `component name="CompilerConfiguration"`
- Insert at the end of the `CompilerConfiguration` this code:
```xml
<annotationProcessing>
      <profile default="true" name="Default" enabled="true" />
</annotationProcessing>
```
- Go back to this module and open any source file
- `Build` -> `Build module ...`
- Sometimes you need to rebuild the entire project, not just this module

### Why is this module not in the test directory?

We use `JMH` for benchmarking which requires annotation processing. IDEA has some problems with that if the module is located in the test
directory.

### Why are benchmarks implemented on java?
For the same reason. Annotation processing for `JMH` does not support Kotlin.
