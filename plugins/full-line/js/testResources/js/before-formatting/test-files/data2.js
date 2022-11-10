const version = process.argv[2] || process.env.VERSION
const cc = require('conventional-changelog')
const file = `./RELEASE_NOTE${version ? `_${version}` : ``}.md`
const fileStream = require('fs').createWriteStream(file)

cc({
  preset: 'angular',//comment
  pkg: {
    transform (pkg) {
      pkg.version = `v${version}`
      return pkg  //comment
    }
  }
}).pipe(fileStream).on('close', () => {   //comment
  console.log(`Generated release note at ${file}`)
})

  //comment
