// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ikv;

import com.intellij.util.lang.Xor16;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/*
Benchmark                                 Mode  Cnt      Score    Error  Units
BloomFilterBenchmark.guavaConstruct       avgt   25   1705,049 ± 13,914  us/op
BloomFilterBenchmark.guavaGet             avgt   25   1691,062 ± 16,181  us/op
BloomFilterBenchmark.ideaConstruct        avgt   25    318,380 ±  0,641  us/op
BloomFilterBenchmark.ideaGet              avgt   25    586,279 ± 11,676  us/op
BloomFilterBenchmark.libFilterConstruct   avgt   25  19744,587 ± 89,751  us/op
BloomFilterBenchmark.libFilterGet         avgt   25    206,051 ±  1,445  us/op
BloomFilterBenchmark.xorConstruct        avgt   15    400.139 ±  1.886  us/op
BloomFilterBenchmark.xorFilterGet        avgt   15    129.594 ±  0.255  us/op

https://gist.github.com/develar/de9a2eb4934e55b281604d70fd00c5e3 - BlockSplitBloomFilter from
https://github.com/apache/parquet-mr/blob/master/parquet-column/src/main/java/org/apache/parquet/column/values/bloomfilter/BlockSplitBloomFilter.java
is not suitable because filter data is very large for 0.005 probability.

https://gist.github.com/develar/974a587f8180e183ce25cf308ffae39a
 */
@SuppressWarnings("CommentedOutCode")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Measurement(time = 5)
@Fork(3)
public class BloomFilterBenchmark {
  private static final double PROBABILITY = 0.005d;

  @State(Scope.Benchmark)
  public static class ConstructState {
    public long[] keys = new long[10_000];

    @Setup
    public void setup() {
      generateKeys(keys);
    }
  }

  @State(Scope.Benchmark)
  public static class IdeaGetState {
    public long[] keys = new long[10_000];
    BloomFilterBase filter;

    @Setup
    public void setup() {
      generateKeys(keys);

      filter = new BloomFilterBase(keys.length, PROBABILITY);
      for (long key : keys) {
        int hash = (int)(key >> 32);
        int hash2 = (int)key;
        filter.addIt(hash, hash2);
      }
    }
  }

  @State(Scope.Benchmark)
  public static class XorFilterGetState {
    public long[] keys = new long[10_000];
    Xor16 filter;

    @Setup
    public void setup() {
      generateKeys(keys);

      filter = Xor16.construct(keys, 0, keys.length);
    }
  }

  //@State(Scope.Benchmark)
  //public static class LibFilterGetState {
  //  public long[] keys = new long[10_000];
  //  BlockFilter filter;
  //
  //  @Setup
  //  public void setup() {
  //    generateKeys(keys);
  //
  //    filter = new BlockFilter(keys.length, PROBABILITY);
  //    for (long key : keys) {
  //      filter.addHash64(key);
  //    }
  //  }
  //}

  //@State(Scope.Benchmark)
  //public static class GuavaGetState {
  //  public long[] keys = new long[10_000];
  //  BloomFilter<Long> filter;
  //
  //  @Setup
  //  public void setup() {
  //    generateKeys(keys);
  //
  //   filter = BloomFilter.create(Funnels.longFunnel(), keys.length, PROBABILITY);
  //    for (long key : keys) {
  //      filter.put(key);
  //    }
  //  }
  //}

  //@State(Scope.Benchmark)
  //public static class BlockSplitGetState {
  //  public long[] keys = new long[10_000];
  //  BlockSplitBloomFilter filter;
  //
  //  @Setup
  //  public void setup() {
  //    generateKeys(keys);
  //
  //    BlockSplitBloomFilter.BloomFilterGenerator generator = new BlockSplitBloomFilter.BloomFilterGenerator(keys.length, PROBABILITY);
  //    for (long key : keys) {
  //      generator.add(key);
  //    }
  //    filter = new BlockSplitBloomFilter(generator.getData());
  //  }
  //}

  @Benchmark
  public BloomFilterBase ideaConstruct(ConstructState state) {
    BloomFilterBase filter = new BloomFilterBase(state.keys.length, PROBABILITY);
    for (long key : state.keys) {
      filter.addIt((int)(key >> 32), (int)key);
    }
    return filter;
  }

  //@Benchmark
  //public BlockFilter libFilterConstruct(ConstructState state) {
  //  BlockFilter filter = new BlockFilter(state.keys.length, PROBABILITY);
  //  for (long key : state.keys) {
  //    filter.addHash64(key);
  //  }
  //  return filter;
  //}

  //@Benchmark
  //public BloomFilter<Long> guavaConstruct(ConstructState state) {
  //  BloomFilter<Long> filter = BloomFilter.create(Funnels.longFunnel(), state.keys.length, PROBABILITY);
  //  for (long key : state.keys) {
  //    filter.put(key);
  //  }
  //  return filter;
  //}

  @Benchmark
  public Xor16 xorConstruct(ConstructState state) {
    return Xor16.construct(state.keys, 0, state.keys.length);
  }

  @Benchmark
  public void xorFilterGet(XorFilterGetState state, Blackhole blackhole) {
    Xor16 filter = state.filter;
    for (long key : state.keys) {
      blackhole.consume(filter.mightContain(key));
      blackhole.consume(filter.mightContain(key + 1));
    }
  }

  //@Benchmark
  //public BlockSplitBloomFilter.BloomFilterGenerator blockSplitConstruct(ConstructState state) {
  //  BlockSplitBloomFilter.BloomFilterGenerator generator = new BlockSplitBloomFilter.BloomFilterGenerator(state.keys.length, PROBABILITY);
  //  for (long key : state.keys) {
  //    generator.add(key);
  //  }
  //  return generator;
  //}

  @Benchmark
  public void ideaGet(IdeaGetState state, Blackhole blackhole) {
    BloomFilterBase filter = state.filter;
    for (long key : state.keys) {
      blackhole.consume(filter.maybeContains((int)(key >> 32), (int)key));
      blackhole.consume(filter.maybeContains((int)(key >> 32) + 1, (int)key + 1));
    }
  }

  //@Benchmark
  //public void libFilterGet(LibFilterGetState state, Blackhole blackhole) {
  //  BlockFilter filter = state.filter;
  //  for (long key : state.keys) {
  //    blackhole.consume(filter.mightContain(key));
  //    blackhole.consume(filter.mightContain(key + 1));
  //  }
  //}

  //@Benchmark
  //public void guavaGet(GuavaGetState state, Blackhole blackhole) {
  //  BloomFilter<Long> filter = state.filter;
  //  for (long key : state.keys) {
  //    blackhole.consume(filter.mightContain(key));
  //    blackhole.consume(filter.mightContain(key + 1));
  //  }
  //}

  //@Benchmark
  //public void blockSplitGet(BlockSplitGetState state, Blackhole blackhole) {
  //  BlockSplitBloomFilter filter = state.filter;
  //  for (long key : state.keys) {
  //    blackhole.consume(filter.mightContain(key));
  //    blackhole.consume(filter.mightContain(key + 1));
  //  }
  //}

  private static void generateKeys(long[] keys) {
    Random random = new Random(42);
    for (int i = 0, n = keys.length; i < n; i++) {
      keys[i] = random.nextLong();
    }
  }
}

final class BloomFilterBase {
  private final int myHashFunctionCount;
  private final int myBitsCount;
  private final long[] myElementsSet;
  private static final int BITS_PER_ELEMENT = 6;

  BloomFilterBase(int _maxElementCount, double probability) {
    int bitsPerElementFactor = (int)Math.ceil(-Math.log(probability) / (Math.log(2) * Math.log(2)));
    myHashFunctionCount = (int)Math.ceil(bitsPerElementFactor * Math.log(2));

    int bitsCount = _maxElementCount * bitsPerElementFactor;

    if ((bitsCount & 1) == 0) {
      ++bitsCount;
    }
    while (!isPrime(bitsCount)) {
      bitsCount += 2;
    }
    myBitsCount = bitsCount;
    myElementsSet = new long[(bitsCount >> BITS_PER_ELEMENT) + 1];
  }

  private static boolean isPrime(int bits) {
    if ((bits & 1) == 0 || bits % 3 == 0) {
      return false;
    }
    int sqrt = (int)Math.sqrt(bits);
    for (int i = 6; i <= sqrt; i += 6) {
      if (bits % (i - 1) == 0 || bits % (i + 1) == 0) {
        return false;
      }
    }
    return true;
  }

  void addIt(int prime, int prime2) {
    for (int i = 0; i < myHashFunctionCount; ++i) {
      int abs = Math.abs((i * prime + prime2 * (myHashFunctionCount - i)) % myBitsCount);
      myElementsSet[abs >> BITS_PER_ELEMENT] |= (1L << abs);
    }
  }

  boolean maybeContains(int prime, int prime2) {
    for (int i = 0; i < myHashFunctionCount; ++i) {
      int abs = Math.abs((i * prime + prime2 * (myHashFunctionCount - i)) % myBitsCount);
      if ((myElementsSet[abs >> BITS_PER_ELEMENT] & (1L << abs)) == 0) {
        return false;
      }
    }

    return true;
  }

  public int sizeInBytes() {
    return 4 * 2 + myElementsSet.length * 8;
  }
}