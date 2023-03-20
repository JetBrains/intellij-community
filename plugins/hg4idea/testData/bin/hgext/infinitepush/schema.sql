CREATE TABLE `bookmarkstonode` (
  `node` varbinary(64) NOT NULL,
  `bookmark` varbinary(512) NOT NULL,
  `reponame` varbinary(255) NOT NULL,
  PRIMARY KEY (`reponame`,`bookmark`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `bundles` (
  `bundle` varbinary(512) NOT NULL,
  `reponame` varbinary(255) NOT NULL,
  PRIMARY KEY (`bundle`,`reponame`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `nodestobundle` (
  `node` varbinary(64) NOT NULL,
  `bundle` varbinary(512) NOT NULL,
  `reponame` varbinary(255) NOT NULL,
  PRIMARY KEY (`node`,`reponame`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `nodesmetadata` (
  `node` varbinary(64) NOT NULL,
  `message` mediumblob NOT NULL,
  `p1` varbinary(64) NOT NULL,
  `p2` varbinary(64) DEFAULT NULL,
  `author` varbinary(255) NOT NULL,
  `committer` varbinary(255) DEFAULT NULL,
  `author_date` bigint(20) NOT NULL,
  `committer_date` bigint(20) DEFAULT NULL,
  `reponame` varbinary(255) NOT NULL,
  `optional_json_metadata` mediumblob,
  PRIMARY KEY (`reponame`,`node`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
