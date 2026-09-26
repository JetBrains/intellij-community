# https://www.madebymikal.com/quick-hack-extracting-the-contents-of-a-docker-image-to-disk/

# Call me like this:
#  docker-image-extract tarfile.tar extracted

import tarfile
import json
import os
import sys

image_path = sys.argv[1]
extracted_path = sys.argv[2]

image = tarfile.open(image_path)
manifest = json.loads(image.extractfile('manifest.json').read())

for layer in manifest[0]['Layers']:
    print('Found layer: %s' % layer)
    layer_tar = tarfile.open(fileobj=image.extractfile(layer))

    for tarinfo in layer_tar:
        print('  ... %s' % tarinfo.name)
        if tarinfo.isdev():
            print('  --> skip device files')
            continue

        dest = os.path.join(extracted_path, tarinfo.name)
        if not tarinfo.isdir() and os.path.exists(dest):
            print('  --> remove old version of file')
            os.unlink(dest)

        layer_tar.extract(tarinfo, path=extracted_path)