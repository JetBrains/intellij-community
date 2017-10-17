package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.cassandra.CassandraIndexTable;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public interface FSRecordsSource {

  List<RecordInfo> loadRecords(List<RecordId> ids);

  ByteBuffer getContent(ByteBuffer contentHash);

  ByteBuffer readAttr(RecordId recordId, String attrId, int version);

  TIntIntHashMap getTree(int shardId);

  int getShardId(int fileId);

  List<RecordId> getMountedChildren(RecordId id);

  List<IFSRecords.RootRecord> getRoots();

  class RecordInfo {
    public final int id;
    public final String name;
    public final long timestamp;
    public final long length;
    public final int flags;
    public final ByteBuffer contentHash;

    public RecordInfo(int id, String name, long timestamp, long length, int flags, final ByteBuffer contentHash) {
      this.id = id;
      this.name = name;
      this.timestamp = timestamp;
      this.length = length;
      this.flags = flags;
      this.contentHash = contentHash;
    }
  }

  class ShardInfo {
    FSRecordsSource.RecordId root;
    TIntIntHashMap myChildParent;
  }

  class RecordId {
    public final int fileId;
    public final int shardId;
    public RecordId(int fileId, int shardId) {
      this.fileId = fileId;
      this.shardId = shardId;
    }
  }

  class CassandraFSRecordsSource implements FSRecordsSource {

    private final TIntIntHashMap myRevision;
    private final TIntHashSet myBaseRevisions;

    public CassandraFSRecordsSource(TIntIntHashMap revision) {
      myRevision = revision;
      myBaseRevisions = new TIntHashSet(myRevision.getValues());
    }

    @Override
    public List<RecordInfo> loadRecords(List<RecordId> ids) {
      return CassandraIndexTable.getInstance().loadFSRecords(ids);
    }

    @Override
    public ByteBuffer getContent(ByteBuffer contentHash) {
      return CassandraIndexTable.getInstance().getContent(contentHash);
    }

    @Override
    public ByteBuffer readAttr(RecordId recordId, String attrId, int version) {
      return CassandraIndexTable.getInstance().readAttr(recordId, attrId, version);
    }

    @Override
    public TIntIntHashMap getTree(int shardId) {
      List<Integer> is = CassandraIndexTable.getInstance().getTree(shardId);
      TIntIntHashMap tree = new TIntIntHashMap();
      for (int i = 0; i < is.size(); i+=2) {
        tree.put(is.get(i), is.get(i + 1));
      }
      return tree;
    }

    @Override
    public int getShardId(int fileId) {
      List<Integer> shards = CassandraIndexTable.getInstance().getShardIds(fileId);
      for (Integer shard : shards) {
        if (myRevision.containsKey(shard) || myBaseRevisions.contains(shard)) {
          return shard;
        }
      }
      throw new AssertionError("shard not found for fileId = " + fileId);
    }

    @Override
    public List<IFSRecords.RootRecord> getRoots() {
      int[] keys = myRevision.keys();
      ArrayList<Integer> list = new ArrayList<>();
      for (int key : keys) {
        list.add(key);
      }
      return CassandraIndexTable.getInstance().getRoots(list);
    }

    @Override
    public List<RecordId> getMountedChildren(RecordId id) {
      return CassandraIndexTable.getInstance().getMountedChildren(id);
    }
  }
}