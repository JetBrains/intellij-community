require 'date'
require 'pp'
require 'json'

CommitInfo = Struct.new(:hash, :time, :author, :repo_name, :grouping_id)

GIT_ROOTS = %w[/ /contrib /community /community/android /community/android/tools-base /CIDR].freeze

def distance(mina, maxa, minb, maxb)
  if minb <= mina && mina <= maxb || minb <= maxa && maxa <= maxb
    0
  elsif mina >= maxb
    mina - maxb
  else
    minb - maxa
  end
end

def parse_git_log(dir)
  pwd = Dir.pwd
  Dir.chdir(pwd + dir)
  result = `git log --format="%H|%cd|%an" rubymine/191.1564^..HEAD`.split("\n").map do |commit|
    commit_split = commit.split('|')
    CommitInfo.new(commit_split[0], DateTime.parse(commit_split[1]).to_time, commit_split[2], dir)
  end
  Dir.chdir(pwd)
  result
end


repos = GIT_ROOTS.map(&method(:parse_git_log))

grouping_id = 0
repos.each do |repo|
  repo.each_index do |index|
    repo[index].grouping_id = if index > 0 && repo[index].author == repo[index - 1].author
                                repo[index - 1].grouping_id
                              else
                                grouping_id += 1
                              end
  end
end

grouping = repos.map { |repo| repo.group_by(&:grouping_id) }

flatten = grouping.map(&:values).flatten(1).sort_by { |it| it.last.time.to_i }

flatten.each_with_index do |group, index|
  (0..index - 1).each do |prev|
    prev_group = flatten[prev]
    next unless prev_group.first.author == group.first.author
    prev_min = prev_group.map { |commit| commit.time.to_i }.min
    prev_max = prev_group.map { |commit| commit.time.to_i }.max

    group_min = group.map { |commit| commit.time.to_i }.min
    group_max = group.map { |commit| commit.time.to_i }.max

    next if distance(prev_min, prev_max, group_min, group_max) > 300

    group_id = prev_group.first.grouping_id
    group.each { |commit| commit.grouping_id = group_id }
    break
  end
end

commit_groups = repos.flatten.group_by(&:grouping_id)

File.open("changes.json", "w") do |f|
  f.write(JSON.pretty_generate(commit_groups.each { |key, value| commit_groups[key] = value.map(&:to_h) }))
end