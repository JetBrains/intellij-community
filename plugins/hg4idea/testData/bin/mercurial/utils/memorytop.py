# memorytop requires Python 3.4
#
# Usage: set PYTHONTRACEMALLOC=n in the environment of the hg invocation,
# where n>= is the number of frames to show in the backtrace. Put calls to
# memorytop in strategic places to show the current memory use by allocation
# site.

import gc
import tracemalloc


def memorytop(limit=10):
    gc.collect()
    snapshot = tracemalloc.take_snapshot()

    snapshot = snapshot.filter_traces(
        (
            tracemalloc.Filter(False, "<frozen importlib._bootstrap>"),
            tracemalloc.Filter(False, "<frozen importlib._bootstrap_external>"),
            tracemalloc.Filter(False, "<unknown>"),
        )
    )
    stats = snapshot.statistics('traceback')

    total = sum(stat.size for stat in stats)
    print("\nTotal allocated size: %.1f KiB\n" % (total / 1024))
    print("Lines with the biggest net allocations")
    for index, stat in enumerate(stats[:limit], 1):
        print(
            "#%d: %d objects using %.1f KiB"
            % (index, stat.count, stat.size / 1024)
        )
        for line in stat.traceback.format(most_recent_first=True):
            print('    ', line)

    other = stats[limit:]
    if other:
        size = sum(stat.size for stat in other)
        count = sum(stat.count for stat in other)
        print(
            "%s other: %d objects using %.1f KiB"
            % (len(other), count, size / 1024)
        )
    print()
