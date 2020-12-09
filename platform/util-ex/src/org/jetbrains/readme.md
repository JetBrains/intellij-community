All changes were done to fit [MVStore](https://www.h2database.com/html/mvstore.html) into IJ Platform.
MVStore is already an amazing and not-bloated library, but still some changes should be done to seamlessly and perfectly integrate it.

 * Use lz4 instead of lzf. As [LZ4 Java](https://github.com/lz4/lz4-java) library is already bundled and LZ4 outperforms LZF.
 * Remove StreamStore, OffHeapStore, MVRTreeMap, SpatialDataType to reduce class set. As not used for now. Maybe re-added if needed.
 * `createSingleThreadExecutor` changed to use IJ Platform `AppExecutorUtil` (`BoundedTaskExecutor`).
 * Code around file store was changed to reduce code size as Java NIO `Path` can be used directly (IJ Platform requires Java 8).
 * [Netty Buffer](https://netty.io/wiki/using-as-a-generic-library.html#buffer-api) API is used for performance reasons. As [Netty](https://netty.io/) library is already bundled.
 * Store and chunk headers format changed from human-readable hex encoding to binary. See `mvstore.tcl` for reference (it's Hex Fiend [template](https://github.com/ridiculousfish/HexFiend/blob/master/templates/Reference.md)). 