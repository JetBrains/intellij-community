Module for platform code (micro-)benchmarks, preferable JMH-based. 
Module has annotation-processing profile assigned to it, which simplifies running JMH benchmarks.

As of today, such benchmarks are not run in CI and not reported to performance dashboard (perf-lab). 
They are supposed to be created as a part of R&D, and kept here so the decisions made on their basis 
could be re-viewed and re-considered later. 

(It is possible, though, that later we'll decide to include those benchmarks in a CI -- this is still 
a debatable topic.) 

Benchmarks 'src' folder configured as 'Tests sources' to clearly indicate those are not a part of a 
distribution.